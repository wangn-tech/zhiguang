package service

import (
	"context"
	"os"
	"sort"
	"strconv"
	"testing"
	"time"
	"zhiguang/internal/model"
	"zhiguang/internal/repository"
	"zhiguang/internal/store"
)

func TestOutboxIntegration_RelationAndCounterPersistOnlyChangedEvents(t *testing.T) {
	mysqlDSN := os.Getenv("ZHIGUANG_TEST_MYSQL_DSN")
	redisAddr := os.Getenv("ZHIGUANG_TEST_REDIS_ADDR")
	if mysqlDSN == "" || redisAddr == "" {
		t.Skip("skip integration test: set ZHIGUANG_TEST_MYSQL_DSN and ZHIGUANG_TEST_REDIS_ADDR")
	}

	redisPassword := os.Getenv("ZHIGUANG_TEST_REDIS_PASSWORD")
	redisDB := 0
	if raw := os.Getenv("ZHIGUANG_TEST_REDIS_DB"); raw != "" {
		parsed, err := strconv.Atoi(raw)
		if err != nil {
			t.Fatalf("invalid ZHIGUANG_TEST_REDIS_DB=%q: %v", raw, err)
		}
		redisDB = parsed
	}

	ctx := context.Background()

	db, err := store.NewMySQL(mysqlDSN)
	if err != nil {
		t.Fatalf("open mysql: %v", err)
	}
	sqlDB, err := db.DB()
	if err != nil {
		t.Fatalf("db.DB(): %v", err)
	}
	defer sqlDB.Close()

	redisClient := store.NewRedis(redisAddr, redisPassword, redisDB)
	defer redisClient.Close()
	if err := redisClient.Ping(ctx).Err(); err != nil {
		t.Fatalf("ping redis: %v", err)
	}

	if err := db.AutoMigrate(&model.OutboxEvent{}); err != nil {
		t.Fatalf("migrate outbox table: %v", err)
	}

	seed := uint64(time.Now().UnixNano() & 0x3fffffff)
	fromUserID := uint64(800000000 + seed%100000)
	toUserID := fromUserID + 1
	entityID := strconv.FormatUint(fromUserID+10000, 10)
	entityIDNum, _ := strconv.ParseUint(entityID, 10, 64)

	relationFollowingKey := "relation:following:" + strconv.FormatUint(fromUserID, 10)
	relationFollowersKey := "relation:followers:" + strconv.FormatUint(toUserID, 10)
	counterEntityKey := "counter:like:knowpost:" + entityID + ":users"
	counterUserKey := "counter:user:" + strconv.FormatUint(fromUserID, 10) + ":like:entities"
	if err := redisClient.Del(ctx, relationFollowingKey, relationFollowersKey, counterEntityKey, counterUserKey).Err(); err != nil {
		t.Fatalf("cleanup redis keys: %v", err)
	}

	outboxRepo := repository.NewOutboxRepository(db)
	relationRepo := repository.NewRelationRepository(redisClient)
	counterRepo := repository.NewCounterRepository(redisClient)

	relationSvc := NewRelationService(relationRepo, nil, nil, nil, WithRelationEventPublisher(NewRelationOutboxEventPublisher(outboxRepo)))
	counterSvc := NewCounterService(counterRepo, WithCounterEventPublisher(NewCounterOutboxEventPublisher(outboxRepo)))

	start := time.Now().UTC().Add(-1 * time.Second)

	changed, err := relationSvc.Follow(ctx, fromUserID, toUserID)
	if err != nil || !changed {
		t.Fatalf("follow first changed=%v err=%v, want true nil", changed, err)
	}
	changed, err = relationSvc.Follow(ctx, fromUserID, toUserID)
	if err != nil || changed {
		t.Fatalf("follow second changed=%v err=%v, want false nil", changed, err)
	}
	changed, err = relationSvc.Unfollow(ctx, fromUserID, toUserID)
	if err != nil || !changed {
		t.Fatalf("unfollow first changed=%v err=%v, want true nil", changed, err)
	}
	changed, err = relationSvc.Unfollow(ctx, fromUserID, toUserID)
	if err != nil || changed {
		t.Fatalf("unfollow second changed=%v err=%v, want false nil", changed, err)
	}

	action, err := counterSvc.Like(ctx, fromUserID, "knowpost", entityID)
	if err != nil || !action.Changed {
		t.Fatalf("like first changed=%v err=%v, want true nil", action.Changed, err)
	}
	action, err = counterSvc.Like(ctx, fromUserID, "knowpost", entityID)
	if err != nil || action.Changed {
		t.Fatalf("like second changed=%v err=%v, want false nil", action.Changed, err)
	}
	action, err = counterSvc.Unlike(ctx, fromUserID, "knowpost", entityID)
	if err != nil || !action.Changed {
		t.Fatalf("unlike first changed=%v err=%v, want true nil", action.Changed, err)
	}
	action, err = counterSvc.Unlike(ctx, fromUserID, "knowpost", entityID)
	if err != nil || action.Changed {
		t.Fatalf("unlike second changed=%v err=%v, want false nil", action.Changed, err)
	}

	var outboxRows []model.OutboxEvent
	err = db.WithContext(ctx).
		Where("created_at >= ?", start).
		Where("(aggregate_type = ? AND aggregate_id = ?) OR (aggregate_type = ? AND aggregate_id = ?)", "relation", fromUserID, "counter.knowpost", entityIDNum).
		Order("id ASC").
		Find(&outboxRows).Error
	if err != nil {
		t.Fatalf("query outbox rows: %v", err)
	}

	if len(outboxRows) != 4 {
		t.Fatalf("len(outboxRows) = %d, want 4", len(outboxRows))
	}

	types := make([]string, 0, len(outboxRows))
	for _, row := range outboxRows {
		types = append(types, row.Type)
	}
	sort.Strings(types)
	wantTypes := []string{"counter.like.activate", "counter.like.deactivate", "relation.follow", "relation.unfollow"}
	for i := range wantTypes {
		if types[i] != wantTypes[i] {
			t.Fatalf("types[%d] = %s, want %s; all=%v", i, types[i], wantTypes[i], types)
		}
	}

	if err := db.WithContext(ctx).Where("id IN ?", collectOutboxIDs(outboxRows)).Delete(&model.OutboxEvent{}).Error; err != nil {
		t.Fatalf("cleanup outbox rows: %v", err)
	}
}

func collectOutboxIDs(rows []model.OutboxEvent) []uint64 {
	ids := make([]uint64, 0, len(rows))
	for _, row := range rows {
		ids = append(ids, row.ID)
	}
	return ids
}
