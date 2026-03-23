package logx

import "testing"

func TestInit_Dev(t *testing.T) {
	err := Init(Options{Env: "dev", Level: "debug"})
	if err != nil {
		t.Fatalf("Init() error = %v", err)
	}
	if L() == nil {
		t.Fatalf("L() should not be nil")
	}
	Sync()
}

func TestInit_InvalidLevel(t *testing.T) {
	err := Init(Options{Env: "prod", Level: "bad-level"})
	if err == nil {
		t.Fatalf("Init() should fail for invalid level")
	}
}
