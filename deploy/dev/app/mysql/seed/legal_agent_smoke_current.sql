-- Minimal dev-only fixture for the current schema. Fixed IDs make this safe to rerun.
SET NAMES utf8mb4;

INSERT INTO app_user (id, open_id, phone_number, nickname, avatar_url, gender, status)
VALUES (1900000000000001001, 'demo-legal-tenant-openid', '13900001001', '法律助手演示租客', '', 0, 1),
       (1900000000000001002, 'demo-legal-landlord-openid', '13900001002', '法律助手演示房东', '', 0, 1)
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO house (id, house_code, landlord_app_user_id, title, city, district, address, monthly_rent, status)
VALUES (1900000000000001101, 'HOUSE-LEGAL-DEMO-001', 1900000000000001002,
        '法律助手演示房源', '上海市', '浦东新区', '演示路1号', 3200.00, 1)
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO house_publish (id, house_id, landlord_user_id, status, audit_status, deleted)
VALUES (1900000000000001201, 1900000000000001101, 1900000000000001002, 1, 2, 0)
ON DUPLICATE KEY UPDATE status = VALUES(status), audit_status = VALUES(audit_status), deleted = 0;

INSERT INTO chat_conversation
    (id, tenant_user_id, landlord_user_id, conversation_type, house_id, house_publish_id, status)
VALUES (1900000000000001301, 1900000000000001001, 1900000000000001002,
        1, 1900000000000001101, 1900000000000001201, 1)
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO chat_conversation_member (conversation_id, user_id, role_type)
VALUES (1900000000000001301, 1900000000000001001, 1),
       (1900000000000001301, 1900000000000001002, 2)
ON DUPLICATE KEY UPDATE role_type = VALUES(role_type);

INSERT INTO legal_contract
    (id, tenant_id, conversation_id, object_key, original_filename, sha256, extracted_text,
     document_type, locator_precision, status)
VALUES
    (1900000000000001501, 1900000000000001001, 1900000000000001301,
     'dev-smoke/contract-1900000000000001501', '演示租赁合同.txt',
     SHA2('legal-agent-smoke-contract-v1', 256),
     '租金每月3200元，押二付三；房东要求另收不可退还清洁费500元。房屋维修由租客承担全部费用。',
     'TEXT', 'UNPAGED_TEXT', 1)
ON DUPLICATE KEY UPDATE status = VALUES(status);
