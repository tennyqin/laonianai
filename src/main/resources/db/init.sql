-- 创建数据库
CREATE DATABASE IF NOT EXISTS laonianai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE laonianai;

-- 分类表
CREATE TABLE IF NOT EXISTS category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL COMMENT '分类名称',
    sort INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 教程/问题表
CREATE TABLE IF NOT EXISTS tutorial (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL COMMENT '问题标题',
    content TEXT NOT NULL COMMENT '问题答案',
    category_id BIGINT NOT NULL COMMENT '分类ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES category(id)
);

-- 管理员表
CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
    nickname VARCHAR(50) DEFAULT '管理员',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 初始化12个核心分类
INSERT INTO category (name, sort) VALUES
('手机使用', 1),
('微信使用', 2),
('手机安全防骗', 3),
('社保养老金', 4),
('医疗健康', 5),
('生活缴费', 6),
('拍照录像', 7),
('语音视频通话', 8),
('网络WiFi', 9),
('出行交通', 10),
('手机设置', 11),
('日常实用工具', 12);

-- 初始化管理员（密码：123456）
INSERT INTO user (username, password) VALUES
('admin', '$2a$10$8H9w3z8H7y7G6F5D4S3A2B1N9M8L7K6J5H4G3F2E1D0C9B8A7S6D5F4G3H2J1K0L9M8N7B6V5C4X3Z2A1S0');

-- 初始化示例问题
INSERT INTO tutorial (title, content, category_id) VALUES
('手机字体怎么调大', '1.打开手机设置；2.找到“显示与亮度”；3.点击“字体大小”；4.拖动滑块调大，点击完成即可。', 1),
('微信怎么发语音', '1.打开微信聊天框；2.点击左下角“按住说话”按钮；3.按住按钮说话，松开自动发送。', 2),
('怎么防诈骗', '1.不接陌生来电；2.不点击陌生链接；3.不向陌生人转账；4.遇到可疑情况立即报警。', 3);