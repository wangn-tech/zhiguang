package service

import "testing"

func TestNormalizeCounterEntity(t *testing.T) {
	entityType, entityID, err := normalizeCounterEntity("  KnowPost ", " 123 ")
	if err != nil {
		t.Fatalf("normalizeCounterEntity() error = %v", err)
	}
	if entityType != "knowpost" {
		t.Fatalf("entityType = %s, want knowpost", entityType)
	}
	if entityID != "123" {
		t.Fatalf("entityID = %s, want 123", entityID)
	}
}

func TestNormalizeCounterEntity_RejectsEmpty(t *testing.T) {
	if _, _, err := normalizeCounterEntity("", "123"); err == nil {
		t.Fatal("normalizeCounterEntity() expected error for empty entityType")
	}
	if _, _, err := normalizeCounterEntity("knowpost", ""); err == nil {
		t.Fatal("normalizeCounterEntity() expected error for empty entityID")
	}
}

func TestNormalizeCounterMetrics_DefaultFallback(t *testing.T) {
	metrics := normalizeCounterMetrics([]string{"x", " ", "unknown"})
	if len(metrics) != 2 {
		t.Fatalf("len(metrics) = %d, want 2", len(metrics))
	}
	if metrics[0] != "like" || metrics[1] != "fav" {
		t.Fatalf("metrics = %v, want [like fav]", metrics)
	}
}

func TestNormalizeCounterMetrics_DedupeAndOrder(t *testing.T) {
	metrics := normalizeCounterMetrics([]string{"fav", "like", "fav", "LIKE"})
	if len(metrics) != 2 {
		t.Fatalf("len(metrics) = %d, want 2", len(metrics))
	}
	if metrics[0] != "like" || metrics[1] != "fav" {
		t.Fatalf("metrics = %v, want [like fav]", metrics)
	}
}

func TestValidateRelationUsers(t *testing.T) {
	if err := validateRelationUsers(1001, 1002); err != nil {
		t.Fatalf("validateRelationUsers() unexpected error = %v", err)
	}
	if err := validateRelationUsers(0, 1002); err == nil {
		t.Fatal("validateRelationUsers() expected error for fromUserID=0")
	}
	if err := validateRelationUsers(1001, 1001); err == nil {
		t.Fatal("validateRelationUsers() expected error for self follow")
	}
}

func TestNormalizeRelationPage(t *testing.T) {
	limit, offset := normalizeRelationPage(-1, -5)
	if limit != 20 || offset != 0 {
		t.Fatalf("normalizeRelationPage(-1,-5) = (%d,%d), want (20,0)", limit, offset)
	}

	limit, offset = normalizeRelationPage(999, 3)
	if limit != 100 || offset != 3 {
		t.Fatalf("normalizeRelationPage(999,3) = (%d,%d), want (100,3)", limit, offset)
	}
}
