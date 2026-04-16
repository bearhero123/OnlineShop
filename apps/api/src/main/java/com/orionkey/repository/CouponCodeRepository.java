package com.orionkey.repository;

import com.orionkey.entity.CouponCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponCodeRepository extends JpaRepository<CouponCode, UUID> {

    Optional<CouponCode> findByCodeAndIsDeleted(String code, int isDeleted);

    List<CouponCode> findByIsDeletedOrderByCreatedAtDesc(int isDeleted);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CouponCode c WHERE c.code = :code AND c.isDeleted = 0")
    Optional<CouponCode> findByCodeForUpdate(@Param("code") String code);
}
