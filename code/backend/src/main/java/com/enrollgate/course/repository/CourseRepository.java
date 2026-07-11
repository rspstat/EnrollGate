package com.enrollgate.course.repository;

import com.enrollgate.course.domain.Course;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {

    /**
     * 동시성 제어: SELECT ... FOR UPDATE로 행 잠금 후 정원 카운터를 안전하게 갱신하기 위한 조회.
     * 반드시 트랜잭션 안에서 호출해야 락이 유지된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") Long id);
}
