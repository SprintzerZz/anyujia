-- Demo data for an end-to-end legal rental agent assessment.
-- Safe to rerun: all IDs are fixed and upserted.

SET NAMES utf8mb4;

SET @tenant_id = 1900000000000001001;
SET @landlord_id = 1900000000000001002;
SET @house_id = 1900000000000001101;
SET @house_publish_id = 1900000000000001201;
SET @conversation_id = 1900000000000001301;
SET @now_ms = UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000;

INSERT INTO app_user
    (id, open_id, phone_number, nickname, avatar_url, gender, status, created_at, updated_at)
VALUES
    (@tenant_id, 'demo-legal-tenant-openid', '13900001001', '李租客-法务演示', '', 0, 1, NOW(), NOW()),
    (@landlord_id, 'demo-legal-landlord-openid', '13900001002', '王房东-法务演示', '', 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    nickname = VALUES(nickname),
    status = VALUES(status),
    updated_at = NOW();

INSERT INTO tenant_user
    (app_user_id, status, created_at, updated_at)
VALUES
    (@tenant_id, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    updated_at = NOW();

INSERT INTO landlord_user
    (app_user_id, verify_status, status, created_at, updated_at)
VALUES
    (@landlord_id, 2, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    verify_status = VALUES(verify_status),
    status = VALUES(status),
    updated_at = NOW();

INSERT INTO house
    (id, house_code, landlord_app_user_id, title, city, district, address, monthly_rent,
     area_sqm, rent_type, bedroom_count, living_room_count, bathroom_count, deposit_months,
     payment_cycle_months, property_fee, available_from, description, rental_requirements,
     facilities_json, cover_image_url, image_assets_json, status, created_at, updated_at)
VALUES
    (@house_id, 'HOUSE-LEGAL-DEMO-001', @landlord_id, '滨江花园一居室整租', '上海市', '浦东新区',
     '滨江花园 1 栋 1001 室', 3200.00, 48.50, 'whole', 1, 1, 1, 2, 3, 180.00,
     '2026-10-15', '近地铁，家电齐全，承诺拎包入住；房东称水电维修响应快。',
     '不可养宠；入住前一次性支付清洁服务费500元；最多入住2人。',
     JSON_ARRAY('空调', '洗衣机', '冰箱', '热水器'), '', JSON_ARRAY(), 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    landlord_app_user_id = VALUES(landlord_app_user_id),
    title = VALUES(title),
    city = VALUES(city),
    district = VALUES(district),
    address = VALUES(address),
    monthly_rent = VALUES(monthly_rent),
    area_sqm = VALUES(area_sqm),
    rent_type = VALUES(rent_type),
    bedroom_count = VALUES(bedroom_count),
    living_room_count = VALUES(living_room_count),
    bathroom_count = VALUES(bathroom_count),
    deposit_months = VALUES(deposit_months),
    payment_cycle_months = VALUES(payment_cycle_months),
    property_fee = VALUES(property_fee),
    available_from = VALUES(available_from),
    description = VALUES(description),
    rental_requirements = VALUES(rental_requirements),
    facilities_json = VALUES(facilities_json),
    image_assets_json = VALUES(image_assets_json),
    status = VALUES(status),
    updated_at = NOW();

INSERT INTO house_publish
    (id, house_id, landlord_user_id, status, audit_status, audit_remark, audited_by,
     audited_at, deleted, submitted_at, created_at, updated_at)
VALUES
    (@house_publish_id, @house_id, @landlord_id, 1, 2, 'legal demo approved', @landlord_id,
     NOW(), 0, NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE
    house_id = VALUES(house_id),
    landlord_user_id = VALUES(landlord_user_id),
    status = VALUES(status),
    audit_status = VALUES(audit_status),
    audit_remark = VALUES(audit_remark),
    audited_by = VALUES(audited_by),
    audited_at = VALUES(audited_at),
    deleted = VALUES(deleted),
    submitted_at = VALUES(submitted_at),
    updated_at = NOW();

INSERT INTO chat_conversation
    (id, tenant_user_id, landlord_user_id, house_id, house_publish_id, last_message_id,
     last_message_seq, next_message_seq, last_message_time, status, created_at, updated_at)
VALUES
    (@conversation_id, @tenant_id, @landlord_id, @house_id, @house_publish_id,
     1900000000000001406, 6, 6, @now_ms, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    tenant_user_id = VALUES(tenant_user_id),
    landlord_user_id = VALUES(landlord_user_id),
    house_id = VALUES(house_id),
    house_publish_id = VALUES(house_publish_id),
    last_message_id = VALUES(last_message_id),
    last_message_seq = VALUES(last_message_seq),
    next_message_seq = VALUES(next_message_seq),
    last_message_time = VALUES(last_message_time),
    status = VALUES(status),
    updated_at = NOW();

INSERT INTO chat_conversation_member
    (conversation_id, user_id, role_type, unread_count, last_read_message_id,
     last_read_conversation_seq, created_at, updated_at)
VALUES
    (@conversation_id, @tenant_id, 1, 0, 1900000000000001406, 6, NOW(), NOW()),
    (@conversation_id, @landlord_id, 2, 0, 1900000000000001406, 6, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    role_type = VALUES(role_type),
    unread_count = VALUES(unread_count),
    last_read_message_id = VALUES(last_read_message_id),
    last_read_conversation_seq = VALUES(last_read_conversation_seq),
    updated_at = NOW();

INSERT INTO chat_message
    (id, client_message_id, conversation_id, conversation_seq, sender_id, recipient_id,
     type, content, status, visited, created_at)
VALUES
    (1900000000000001401, 'legal-demo-msg-001', @conversation_id, 1, @tenant_id, @landlord_id,
     1, '押金什么时候退？合同里能不能写清楚？', 1, 10, TIMESTAMPADD(SECOND, -300, NOW(3))),
    (1900000000000001402, 'legal-demo-msg-002', @conversation_id, 2, @landlord_id, @tenant_id,
     1, '合同到期后看情况，卫生不满意押金就不退。', 1, 10, TIMESTAMPADD(SECOND, -260, NOW(3))),
    (1900000000000001403, 'legal-demo-msg-003', @conversation_id, 3, @tenant_id, @landlord_id,
     1, '如果你要进门维修，会提前通知我吗？', 1, 10, TIMESTAMPADD(SECOND, -220, NOW(3))),
    (1900000000000001404, 'legal-demo-msg-004', @conversation_id, 4, @landlord_id, @tenant_id,
     1, '我有备用钥匙，检查水电可以直接进房，不用每次通知。', 1, 10, TIMESTAMPADD(SECOND, -180, NOW(3))),
    (1900000000000001405, 'legal-demo-msg-005', @conversation_id, 5, @tenant_id, @landlord_id,
     1, '除了租金和押金，还有别的费用吗？', 1, 10, TIMESTAMPADD(SECOND, -140, NOW(3))),
    (1900000000000001406, 'legal-demo-msg-006', @conversation_id, 6, @landlord_id, @tenant_id,
     1, '签约时另外收500元清洁服务费，合同里不写也要交。', 1, 10, TIMESTAMPADD(SECOND, -100, NOW(3)))
ON DUPLICATE KEY UPDATE
    content = VALUES(content),
    status = VALUES(status),
    visited = VALUES(visited),
    created_at = VALUES(created_at);

INSERT INTO chat_user_conversation
    (user_id, conversation_id, role_type, other_user_id, house_id, house_publish_id,
     last_message_id, last_message_seq, last_message_time, unread_count,
     last_read_message_id, last_read_conversation_seq, status, created_at, updated_at)
VALUES
    (@tenant_id, @conversation_id, 1, @landlord_id, @house_id, @house_publish_id,
     1900000000000001406, 6, @now_ms, 0, 1900000000000001406, 6, 1, NOW(), NOW()),
    (@landlord_id, @conversation_id, 2, @tenant_id, @house_id, @house_publish_id,
     1900000000000001406, 6, @now_ms, 0, 1900000000000001406, 6, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    role_type = VALUES(role_type),
    other_user_id = VALUES(other_user_id),
    house_id = VALUES(house_id),
    house_publish_id = VALUES(house_publish_id),
    last_message_id = VALUES(last_message_id),
    last_message_seq = VALUES(last_message_seq),
    last_message_time = VALUES(last_message_time),
    unread_count = VALUES(unread_count),
    last_read_message_id = VALUES(last_read_message_id),
    last_read_conversation_seq = VALUES(last_read_conversation_seq),
    status = VALUES(status),
    updated_at = NOW();
