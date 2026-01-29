package com.backend.domain.pain;

import com.backend.domain.member.Member;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "pain_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PainLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;
    
    private String area; // 통증 부위 (예: "무릎", "어깨", "허리")
    
    @Column(columnDefinition = "TEXT")
    private String description; // 통증 설명
    
    private int intensity; // 통증 강도 (1~10)
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "is_related_to_exercise")
    private boolean isRelatedToExercise; // 오늘 루틴의 운동과 관련된 통증인지
}
