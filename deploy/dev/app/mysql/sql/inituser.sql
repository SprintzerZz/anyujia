-- # 1銆佸垵濮嬪寲鏁版嵁搴擄細鍒涘缓nacos澶栫疆鏁版嵁搴揻rameworkjava_nacos_dev鍜岃剼鎵嬫灦涓氬姟鏁版嵁搴?
-- frameworkjava_dev
-- # 2銆佸垱寤虹敤鎴凤紝鐢ㄦ埛鍚嶏細appdev 瀵嗙爜锛歴pz@123
-- # 3銆佹巿浜坰pzdev鐢ㄦ埛鐗瑰畾鏉冮檺
CREATE database if NOT EXISTS `frameworkjava_nacos_dev` default character set
utf8mb4 collate utf8mb4_general_ci;

CREATE database if NOT EXISTS `frameworkjava_dev` default character set
utf8mb4 collate utf8mb4_general_ci;

CREATE USER 'appdev'@'%' IDENTIFIED BY 'change-me';
GRANT replication slave, replication client on *.* to 'appdev'@'%';
GRANT ALL PRIVILEGES ON frameworkjava_nacos_dev.* TO 'appdev'@'%';
GRANT ALL PRIVILEGES ON frameworkjava_dev.* TO 'appdev'@'%';
FLUSH PRIVILEGES;
