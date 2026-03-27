package repository

import (
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
