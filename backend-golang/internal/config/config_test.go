package config

import (
	"os"
	"path/filepath"
	"testing"
)

func TestLoadFromPath_ConfigFile(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "config.yaml")
	content := []byte(`server:
  port: "7070"
  env: "stage"
mysql:
  dsn: "root:file@tcp(127.0.0.1:3306)/zhiguang?parseTime=true"
redis:
  addr: "127.0.0.1:6381"
  password: "from-file"
  db: 3
`)
	if err := os.WriteFile(path, content, 0o644); err != nil {
		t.Fatalf("write config file: %v", err)
	}

	cfg, err := LoadFromPath(path)
	if err != nil {
		t.Fatalf("LoadFromPath() error = %v", err)
	}

	if cfg.Server.Port != "7070" {
		t.Fatalf("Server.Port = %s, want 7070", cfg.Server.Port)
	}
	if cfg.Server.Env != "stage" {
		t.Fatalf("Server.Env = %s, want stage", cfg.Server.Env)
	}
	if cfg.Redis.DB != 3 {
		t.Fatalf("Redis.DB = %d, want 3", cfg.Redis.DB)
	}
}

func TestLoadFromPath_DefaultWhenFieldMissing(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "config.yaml")
	content := []byte(`server:
  port: "7070"
`)
	if err := os.WriteFile(path, content, 0o644); err != nil {
		t.Fatalf("write config file: %v", err)
	}

	cfg, err := LoadFromPath(path)
	if err != nil {
		t.Fatalf("LoadFromPath() error = %v", err)
	}

	if cfg.Server.Port != "7070" {
		t.Fatalf("Server.Port = %s, want 7070", cfg.Server.Port)
	}
	if cfg.Server.Env != "dev" {
		t.Fatalf("Server.Env = %s, want dev", cfg.Server.Env)
	}
	if cfg.Redis.DB != 0 {
		t.Fatalf("Redis.DB = %d, want 0", cfg.Redis.DB)
	}
}

func TestLoadFromPath_EmptyPathUsesDefaultConfigPath(t *testing.T) {
	originWD, err := os.Getwd()
	if err != nil {
		t.Fatalf("os.Getwd() error = %v", err)
	}

	dir := t.TempDir()
	configDir := filepath.Join(dir, "configs")
	if err := os.MkdirAll(configDir, 0o755); err != nil {
		t.Fatalf("os.MkdirAll() error = %v", err)
	}

	content := []byte(`server:
  port: "8181"
`)
	if err := os.WriteFile(filepath.Join(configDir, "config.yaml"), content, 0o644); err != nil {
		t.Fatalf("write config file: %v", err)
	}

	if err := os.Chdir(dir); err != nil {
		t.Fatalf("os.Chdir() error = %v", err)
	}
	t.Cleanup(func() {
		if chdirErr := os.Chdir(originWD); chdirErr != nil {
			t.Fatalf("restore working directory: %v", chdirErr)
		}
	})

	cfg, err := LoadFromPath("")
	if err != nil {
		t.Fatalf("LoadFromPath() error = %v", err)
	}
	if cfg.Server.Port != "8181" {
		t.Fatalf("Server.Port = %s, want 8181", cfg.Server.Port)
	}
}

func TestLoadFromPath_FileNotFound(t *testing.T) {
	_, err := LoadFromPath(filepath.Join(t.TempDir(), "missing.yaml"))
	if err == nil {
		t.Fatalf("LoadFromPath() should fail when config file does not exist")
	}
}
