package store

import (
	"context"

	"gorm.io/driver/mysql"
	"gorm.io/gorm"
)

// MySQLChecker pings MySQL for readiness checks.
type MySQLChecker struct {
	db *gorm.DB
}

// NewMySQL 创建 GORM MySQL 客户端。
func NewMySQL(dsn string) (*gorm.DB, error) {
	return gorm.Open(mysql.Open(dsn), &gorm.Config{})
}

// NewMySQLChecker 创建 MySQL 就绪检查器。
func NewMySQLChecker(db *gorm.DB) *MySQLChecker {
	return &MySQLChecker{db: db}
}

// Name 返回依赖名称。
func (c *MySQLChecker) Name() string {
	return "mysql"
}

// Check 执行 MySQL 连通性探测。
func (c *MySQLChecker) Check(ctx context.Context) error {
	sqlDB, err := c.db.DB()
	if err != nil {
		return err
	}
	return sqlDB.PingContext(ctx)
}
