package com.anyujia.anyujiahouseservice.mapper;

import com.anyujia.anyujiahouseservice.domain.HousePublishRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LandlordHousePublishMapper {
    @Select("""
            SELECT COUNT(*)
            FROM landlord_user
            WHERE app_user_id = #{userId} AND verify_status = 2 AND status = 1
            """)
    int countApprovedLandlord(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO house
                (house_code, landlord_app_user_id, title, city, district, address, monthly_rent,
                 area_sqm, rent_type, bedroom_count, living_room_count, bathroom_count,
                 deposit_months, payment_cycle_months, property_fee, available_from, description,
                 rental_requirements, facilities_json, cover_image_url, image_assets_json,
                 status, created_at, updated_at)
            VALUES
                (#{row.houseCode}, #{landlordId}, #{row.title}, #{row.city}, #{row.district},
                 #{row.address}, #{row.monthlyRent}, #{row.areaSqm}, #{row.rentType}, #{row.bedroomCount},
                 #{row.livingRoomCount}, #{row.bathroomCount}, #{row.depositMonths},
                 #{row.paymentCycleMonths}, #{row.propertyFee}, #{row.availableFrom}, #{row.description},
                 #{row.rentalRequirements}, #{row.facilitiesJson}, #{row.coverImageUrl},
                 #{row.imageAssetsJson}, 1, NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.houseId", keyColumn = "id")
    int insertHouse(@Param("row") HousePublishRow row, @Param("landlordId") Long landlordId);

    @Insert("""
            INSERT INTO house_publish
                (id, house_id, landlord_user_id, status, audit_status, deleted, submitted_at, created_at, updated_at)
            VALUES
                (#{publishId}, #{houseId}, #{landlordId}, #{status}, 0, 0, #{submittedAt}, NOW(), NOW())
            """)
    int insertPublication(@Param("publishId") Long publishId,
                          @Param("houseId") Long houseId,
                          @Param("landlordId") Long landlordId,
                          @Param("status") Integer status,
                          @Param("submittedAt") LocalDateTime submittedAt);

    @Select("""
            SELECT hp.id AS publishId, h.id AS houseId, h.house_code AS houseCode,
                   h.title AS title, h.city AS city, h.district AS district, h.address AS address,
                   h.monthly_rent AS monthlyRent, h.area_sqm AS areaSqm, h.rent_type AS rentType,
                   h.bedroom_count AS bedroomCount, h.living_room_count AS livingRoomCount,
                   h.bathroom_count AS bathroomCount, h.deposit_months AS depositMonths,
                   h.payment_cycle_months AS paymentCycleMonths, h.property_fee AS propertyFee,
                   h.available_from AS availableFrom, h.description AS description,
                   h.rental_requirements AS rentalRequirements, h.facilities_json AS facilitiesJson,
                   h.cover_image_url AS coverImageUrl, hp.status AS status, hp.audit_status AS auditStatus,
                   hp.submitted_at AS submittedAt, hp.created_at AS createdAt
                   , hp.audit_remark AS auditRemark, hp.audited_by AS auditedBy, hp.audited_at AS auditedAt,
                   h.image_assets_json AS imageAssetsJson
            FROM house_publish hp
            JOIN house h ON h.id = hp.house_id
            WHERE hp.landlord_user_id = #{landlordId} AND hp.deleted = 0
            ORDER BY hp.created_at DESC, hp.id DESC
            LIMIT 100
            """)
    List<HousePublishRow> selectMine(@Param("landlordId") Long landlordId);

    @Select("""
            SELECT hp.id AS publishId, h.id AS houseId, h.house_code AS houseCode,
                   h.title AS title, h.city AS city, h.district AS district, h.address AS address,
                   h.monthly_rent AS monthlyRent, h.area_sqm AS areaSqm, h.rent_type AS rentType,
                   h.bedroom_count AS bedroomCount, h.living_room_count AS livingRoomCount,
                   h.bathroom_count AS bathroomCount, h.deposit_months AS depositMonths,
                   h.payment_cycle_months AS paymentCycleMonths, h.property_fee AS propertyFee,
                   h.available_from AS availableFrom, h.description AS description,
                   h.rental_requirements AS rentalRequirements, h.facilities_json AS facilitiesJson,
                   h.cover_image_url AS coverImageUrl, hp.status AS status, hp.audit_status AS auditStatus,
                   hp.submitted_at AS submittedAt, hp.created_at AS createdAt,
                   hp.audit_remark AS auditRemark, hp.audited_by AS auditedBy, hp.audited_at AS auditedAt,
                   h.image_assets_json AS imageAssetsJson
            FROM house_publish hp
            JOIN house h ON h.id = hp.house_id
            WHERE hp.id = #{publishId} AND hp.landlord_user_id = #{landlordId} AND hp.deleted = 0
            LIMIT 1
            """)
    HousePublishRow selectOwned(@Param("publishId") Long publishId, @Param("landlordId") Long landlordId);

    @Update("""
            UPDATE house_publish
            SET audit_status = #{auditStatus},
                status = CASE WHEN status = 0 AND #{auditStatus} = 2 THEN 1 ELSE status END,
                audit_remark = #{remark},
                audited_by = #{auditorId},
                audited_at = NOW(),
                updated_at = NOW()
            WHERE id = #{publishId}
              AND deleted = 0
              AND status IN (0, 1)
            """)
    int audit(@Param("publishId") Long publishId,
              @Param("auditStatus") Integer auditStatus,
              @Param("remark") String remark,
              @Param("auditorId") Long auditorId);

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
            WHERE hp.id = #{publishId} AND hp.deleted = 0
            LIMIT 1
            """)
    HousePublishRow selectByPublishId(@Param("publishId") Long publishId);
}
