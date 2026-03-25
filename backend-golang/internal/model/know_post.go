package model

// KnowPost 对应 know_posts 表的最小字段模型。
type KnowPost struct {
	ID        uint64 `gorm:"column:id;primaryKey"`
	CreatorID uint64 `gorm:"column:creator_id"`
}

// TableName 指定 know_posts 表名。
func (KnowPost) TableName() string {
	return "know_posts"
}
