package com.anyujia.anyujiahouseservice.mapper;

import com.anyujia.anyujiahouseapi.domain.vo.HousePublishConsultVO;
import com.anyujia.anyujiahouseservice.domain.HousePublishRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface HousePublishMapper {
    @Select("""
            SELECT hp.id AS housePublishId,
                   hp.house_id AS houseId,
                   hp.landlord_user_id AS landlordAppUserId
            FROM house_publish hp
            JOIN house h ON h.id = hp.house_id
            WHERE hp.id = #{housePublishId}
              AND hp.status = 1
              AND hp.audit_status = 2
              AND hp.deleted = 0
              AND h.status = 1
              AND h.landlord_app_user_id = hp.landlord_user_id
            LIMIT 1
            """)
    HousePublishConsultVO selectConsultable(@Param("housePublishId") Long housePublishId);

    @Select("""
            SELECT hp.id AS publishId, h.id AS houseId, h.house_code AS houseCode,
                   h.title AS title, h.city AS city, h.district AS district, h.address AS address,
                   h.monthly_rent AS monthlyRent, h.area_sqm AS areaSqm, h.rent_type AS rentType,
                   h.bedroom_count AS bedroomCount, h.living_room_count AS livingRoomCount,
                   h.bathroom_count AS bathroomCount, h.deposit_months AS depositMonths,
                   h.payment_cycle_months AS paymentCycleMonths, h.property_fee AS propertyFee,
                   h.available_from AS availableFrom, h.description AS description,
                   h.rental_requirements AS rentalRequirements, h.facilities_json AS facilitiesJson,
                   h.cover_image_url AS coverImageUrl, h.image_assets_json AS imageAssetsJson,
                   hp.status AS status, hp.audit_status AS auditStatus, hp.submitted_at AS submittedAt,
                   hp.created_at AS createdAt, hp.audit_remark AS auditRemark,
                   hp.audited_by AS auditedBy, hp.audited_at AS auditedAt
            FROM house_publish hp
            JOIN house h ON h.id = hp.house_id
            WHERE hp.status = 1
              AND hp.audit_status = 2
              AND hp.deleted = 0
              AND h.status = 1
              AND h.landlord_app_user_id = hp.landlord_user_id
            ORDER BY hp.audited_at DESC, hp.created_at DESC, hp.id DESC
            LIMIT #{limit}
            """)
    List<HousePublishRow> selectPublicList(@Param("limit") int limit);

    @Select("""
            SELECT hp.id AS publishId, h.id AS houseId, h.house_code AS houseCode,
                   h.title AS title, h.city AS city, h.district AS district, h.address AS address,
                   h.monthly_rent AS monthlyRent, h.area_sqm AS areaSqm, h.rent_type AS rentType,
                   h.bedroom_count AS bedroomCount, h.living_room_count AS livingRoomCount,
                   h.bathroom_count AS bathroomCount, h.deposit_months AS depositMonths,
                   h.payment_cycle_months AS paymentCycleMonths, h.property_fee AS propertyFee,
                   h.available_from AS availableFrom, h.description AS description,
                   h.rental_requirements AS rentalRequirements, h.facilities_json AS facilitiesJson,
                   h.cover_image_url AS coverImageUrl, h.image_assets_json AS imageAssetsJson,
                   hp.status AS status, hp.audit_status AS auditStatus, hp.submitted_at AS submittedAt,
                   hp.created_at AS createdAt, hp.audit_remark AS auditRemark,
                   hp.audited_by AS auditedBy, hp.audited_at AS auditedAt
            FROM house_publish hp
            JOIN house h ON h.id = hp.house_id
            WHERE hp.id = #{publishId}
              AND hp.status = 1
              AND hp.audit_status = 2
              AND hp.deleted = 0
              AND h.status = 1
              AND h.landlord_app_user_id = hp.landlord_user_id
            LIMIT 1
            """)
    HousePublishRow selectPublicDetail(@Param("publishId") Long publishId);
}
