package com.enrollgate.course.repository;

import com.enrollgate.course.domain.Course;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {

    /**
     * 동시성 제어(방식 A, 비관적 락): SELECT ... FOR UPDATE로 행 잠금 후 정원 카운터를 안전하게 갱신하기 위한 조회.
     * 반드시 트랜잭션 안에서 호출해야 락이 유지된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") Long id);

    /**
     * 동시성 제어(방식 B, Redis 원자 연산): 이미 Redis EVAL이 정원 초과 여부를 원자적으로 확정한 뒤 호출하는
     * "이미 허가된" 증가이므로, 앞선 SELECT 없이 단일 UPDATE 문 자체의 원자성만으로 안전하다.
     */
    @Modifying
    @Query("update Course c set c.currentEnrolledCount = c.currentEnrolledCount + 1 where c.id = :id")
    int incrementEnrolledCount(@Param("id") Long id);

    List<Course> findBySemesterAndDepartment(String semester, String department);

    List<Course> findBySemester(String semester);

    List<Course> findByDepartment(String department);
}
