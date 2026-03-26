package repository

import (
	"context"
	"testing"
)

func TestRelationCursorMax_UsesExclusiveBound(t *testing.T) {
	got := relationCursorMax(1711449600123)
	want := "(1711449600123"
	if got != want {
		t.Fatalf("relationCursorMax() = %s, want %s", got, want)
	}
}

func TestRelationKeyBuilders(t *testing.T) {
	if got := relationFollowingKey(1001); got != "relation:following:1001" {
		t.Fatalf("relationFollowingKey() = %s, want relation:following:1001", got)
	}
	if got := relationFollowersKey(1002); got != "relation:followers:1002" {
		t.Fatalf("relationFollowersKey() = %s, want relation:followers:1002", got)
	}
}

func TestRelationRepository_ListIDs_ZeroLimitReturnsEmpty(t *testing.T) {
	repo := &RelationRepository{}
	ids, err := repo.listIDs(context.Background(), "relation:following:1001", 0, 0, nil)
	if err != nil {
		t.Fatalf("listIDs() error = %v", err)
	}
	if len(ids) != 0 {
		t.Fatalf("len(ids) = %d, want 0", len(ids))
	}
}
