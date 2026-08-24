-- Canal 以复制协议读取 MySQL ROW binlog，仅订阅 zhiguang.outbox。
-- 此脚本仅在 MySQL 数据卷首次创建时由官方镜像执行。
CREATE USER IF NOT EXISTS 'canal'@'%' IDENTIFIED BY 'canal';
GRANT SELECT, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'canal'@'%';
FLUSH PRIVILEGES;
