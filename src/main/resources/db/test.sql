SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for category
-- ----------------------------
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL COMMENT '分类名称',
  `sort` int DEFAULT '0' COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of category
-- ----------------------------
BEGIN;
INSERT INTO `category` (`id`, `name`, `sort`, `create_time`, `update_time`) VALUES (1, '手机使用', 1, '2026-03-03 07:41:52', '2026-03-03 07:41:52');
INSERT INTO `category` (`id`, `name`, `sort`, `create_time`, `update_time`) VALUES (2, '微信使用', 2, '2026-03-03 07:41:52', '2026-03-03 07:41:52');
INSERT INTO `category` (`id`, `name`, `sort`, `create_time`, `update_time`) VALUES (3, '手机安全防骗', 3, '2026-03-03 07:41:52', '2026-03-03 07:41:52');
INSERT INTO `category` (`id`, `name`, `sort`, `create_time`, `update_time`) VALUES (4, '社保养老金', 4, '2026-03-03 07:41:52', '2026-03-03 07:41:52');
INSERT INTO `category` (`id`, `name`, `sort`, `create_time`, `update_time`) VALUES (5, '医疗健康', 5, '2026-03-03 07:41:52', '2026-03-03 07:41:52');
INSERT INTO `category` (`id`, `name`, `sort`, `create_time`, `update_time`) VALUES (6, '生活缴费', 6, '2026-03-03 07:41:52', '2026-03-03 07:41:52');
INSERT INTO `category` (`id`, `name`, `sort`, `create_time`, `update_time`) VALUES (7, '拍照录像', 7, '2026-03-03 07:41:52', '2026-03-03 07:41:52');
INSERT INTO `category` (`id`, `name`, `sort`, `create_time`, `update_time`) VALUES (8, '语音视频通话', 8, '2026-03-03 07:41:52', '2026-03-03 07:41:52');
INSERT INTO `category` (`id`, `name`, `sort`, `create_time`, `update_time`) VALUES (9, '网络WiFi', 9, '2026-03-03 07:41:52', '2026-03-03 07:41:52');
INSERT INTO `category` (`id`, `name`, `sort`, `create_time`, `update_time`) VALUES (10, '出行交通', 10, '2026-03-03 07:41:52', '2026-03-03 07:41:52');
INSERT INTO `category` (`id`, `name`, `sort`, `create_time`, `update_time`) VALUES (11, '手机设置', 11, '2026-03-03 07:41:52', '2026-03-03 07:41:52');
INSERT INTO `category` (`id`, `name`, `sort`, `create_time`, `update_time`) VALUES (12, '日常实用工具', 12, '2026-03-03 07:41:52', '2026-03-03 07:41:52');
COMMIT;

-- ----------------------------
-- Table structure for tutorial
-- ----------------------------
DROP TABLE IF EXISTS `tutorial`;
CREATE TABLE `tutorial` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(100) NOT NULL COMMENT '问题标题',
  `content` text NOT NULL COMMENT '问题答案',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `url` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `category_id` (`category_id`),
  CONSTRAINT `tutorial_ibfk_1` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of tutorial
-- ----------------------------
BEGIN;
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (1, '手机字体调大最简单方法', '1. 打开手机设置；2. 找到“显示”；3. 点“字体大小”；4. 把滑块往右拉，字就变大了。', 1, 'shouji-ziti-tiaoda', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (2, '手机声音调大的步骤', '1. 按手机侧面的“+”键；2. 屏幕会显示音量条；3. 调到最大就可以了。', 1, 'shouji-shengyin-tiaoda', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (3, '手机怎么截图', '1. 同时按电源键+音量减键；2. 听到“咔嚓”声就截好了；3. 去相册能找到截图。', 1, 'shouji-zenme-jietu', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (4, '手机怎么连接WiFi', '1. 打开设置；2. 点“WLAN”；3. 选家里的WiFi名字；4. 输入密码，点连接。', 1, 'shouji-lianjie-wifi', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (5, '手机怎么充电更耐用', '1. 电量剩20%就充；2. 充满就拔，别充一夜；3. 用原装充电器。', 1, 'shouji-chongdian-ngaiyong', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (6, '手机怎么调亮度', '1. 打开设置；2. 点“显示”；3. 滑动“亮度”滑块；4. 调舒服的亮度就行。', 1, 'shouji-tiaoliangdu', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (7, '手机怎么开静音', '1. 按侧面的音量减键到底；2. 屏幕显示“静音”就好了；3. 也可以在设置里开。', 1, 'shouji-kai-jingyin', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (8, '手机相册怎么找照片', '1. 打开“相册”APP；2. 点“全部照片”；3. 往下滑就能翻照片。', 1, 'shouji-xiangce-zhaozhaopian', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (9, '手机怎么关广告', '1. 打开设置；2. 点“通知”；3. 找到弹广告的APP；4. 关掉“允许通知”。', 1, 'shouji-guan-guanggao', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (10, '手机怎么重启', '1. 长按电源键；2. 点“重启”；3. 等几秒手机就重新开了。', 1, 'shouji-zenme-chongqi', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (11, '微信怎么发语音', '1. 打开微信聊天框；2. 点右下角“按住说话”；3. 说完松开，就发出去了。', 2, 'weixin-fa-yuyin', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (12, '微信怎么发照片', '1. 打开聊天框；2. 点右下角“+”；3. 点“照片”；4. 选照片，点发送。', 2, 'weixin-fa-zhaopian', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (13, '微信怎么视频通话', '1. 打开聊天框；2. 点右上角“+”；3. 点“视频通话”；4. 等对方接就行。', 2, 'weixin-shipin-tonghua', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (14, '微信怎么加好友', '1. 点右上角“+”；2. 点“添加朋友”；3. 输手机号/微信号；4. 点“添加”。', 2, 'weixin-jia-haoyou', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (15, '微信怎么发红包', '1. 打开聊天框；2. 点右下角“+”；3. 点“红包”；4. 输金额，点“塞钱进红包”。', 2, 'weixin-fa-hongbao', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (16, '微信怎么删聊天记录', '1. 长按聊天框；2. 点“删除”；3. 确认删除就可以了。', 2, 'weixin-shan-liaotianjilu', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (17, '微信怎么改名字', '1. 点“我”；2. 点头像；3. 点“昵称”；4. 改名字，点保存。', 2, 'weixin-gai-mingzi', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (18, '微信怎么查余额', '1. 点“我”；2. 点“服务”；3. 点“钱包”；4. 就能看到余额了。', 2, 'weixin-cha-yue', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (19, '微信怎么退群', '1. 打开微信群；2. 点右上角“...”；3. 滑到底，点“退出群聊”。', 2, 'weixin-tui-qun', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (20, '微信怎么清缓存', '1. 点“我”-“设置”；2. 点“通用”；3. 点“微信存储空间”；4. 点“清理”。', 2, 'weixin-qing-huancun', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (21, '怎么防电话诈骗', '1. 陌生电话说中奖、欠钱都挂；2. 不告诉对方验证码；3. 不转钱给陌生人。', 3, 'fang-dianhua-zha pian', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (22, '怎么防短信诈骗', '1. 陌生短信不点开链接；2. 不回短信；3. 直接删掉可疑短信。', 3, 'fang-duanxin-zha pian', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (23, '怎么防微信诈骗', '1. 不加陌生微信；2. 不扫陌生二维码；3. 不转钱给微信陌生人。', 3, 'fang-weixin-zha pian', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (24, '验证码不能告诉谁', '1. 银行、警察、客服都不会要验证码；2. 只要要验证码的都是骗子；3. 验证码自己留着。', 3, 'yanzhengma-bu-neng-gaosu shui', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (25, '怎么识别假链接', '1. 链接里有奇怪的字母数字；2. 点之前看域名，不是官网就别点；3. 直接删掉。', 3, 'shibie-jialianjie', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (26, '怎么防刷单诈骗', '1. 刷单都是骗子；2. 先交钱的都是假的；3. 不理会刷单的广告。', 3, 'fang-shuadan-zha pian', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (27, '怎么防冒充客服诈骗', '1. 自己打官方电话核实；2. 不接陌生客服电话；3. 不按对方说的操作。', 3, 'fang-maochong-kefu-zha pian', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (28, '被骗了怎么办', '1. 立刻报警；2. 保存聊天记录、转账记录；3. 告诉家人。', 3, 'beipian-liao-zenmeban', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (29, '怎么设置手机密码', '1. 打开设置；2. 点“密码与安全”；3. 设6位数密码；4. 确认密码。', 3, 'shezhi-shouji-mima', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
INSERT INTO `tutorial` (`id`, `title`, `content`, `category_id`, `url`, `create_time`, `update_time`) VALUES (30, '怎么删陌生APP', '1. 打开设置；2. 点“应用”；3. 找到陌生APP；4. 点“卸载”。', 3, 'shan-mosheng-app', '2026-03-03 09:04:53', '2026-03-03 09:04:53');
COMMIT;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码（BCrypt加密）',
  `nickname` varchar(50) DEFAULT '管理员',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of user
-- ----------------------------
BEGIN;
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `create_time`) VALUES (1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '管理员', '2026-03-03 07:41:52');
COMMIT;

SET FOREIGN_KEY_CHECKS = 1;
