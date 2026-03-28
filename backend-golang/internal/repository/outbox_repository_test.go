package repository

import (
	"context"
	"testing"
	"time"
)

func TestNextOutboxID_IncreasesWithSequence(t *testing.T) {
	now := time.UnixMilli(1711449600123)
	first := nextOutboxID(now)
	second := nextOutboxID(now)
	if second <= first {
		t.Fatalf("nextOutboxID second=%d, first=%d; want second > first", second, first)
	}
}

func TestNextOutboxID_ContainsTimestampBits(t *testing.T) {
	now := time.UnixMilli(1711449600123)
	id := nextOutboxID(now)
	if got := id >> 12; got != uint64(now.UnixMilli()) {
		t.Fatalf("timestamp bits = %d, want %d", got, now.UnixMilli())
	}
}

func TestOutboxRepository_ListAfterID_ZeroLimitReturnsEmpty(t *testing.T) {
	repo := &OutboxRepository{}
	rows, err := repo.ListAfterID(context.Background(), 0, 0)
	if err != nil {
		t.Fatalf("ListAfterID() error = %v", err)
	}
	if len(rows) != 0 {
		t.Fatalf("len(rows) = %d, want 0", len(rows))
	}
}
