package com.backend.controller.member;

import com.backend.domain.member.Member;
import com.backend.domain.member.Target;
import com.backend.dto.response.MemberResponse;
import com.backend.repository.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
@Slf4j
public class MemberController {
    
    private final MemberRepository memberRepository;
    
    @GetMapping("/{memberId}")
    public ResponseEntity<MemberResponse> getMember(@PathVariable Long memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new RuntimeException("멤버를 찾을 수 없습니다: " + memberId));
        
        MemberResponse response = MemberResponse.builder()
            .id(member.getId())
            .name(member.getName())
            .target(member.getTarget() != null ? member.getTarget().name() : null)
            .physicalInfo(member.getPhysicalInfo())
            .build();
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/current")
    @Transactional
    public ResponseEntity<MemberResponse> getCurrentMember() {
        // TODO: 실제 인증에서 memberId 가져오기 (현재는 임시로 멤버 4 또는 8 우선 사용)
        // 멤버 4 또는 8이 있으면 우선 사용 (더미데이터가 있는 멤버), 없으면 첫 번째 멤버 사용
        List<Member> allMembers = memberRepository.findAll();
        Member member = allMembers.stream()
            .filter(m -> m.getId() == 4L || m.getId() == 8L)
            .findFirst()
            .orElseGet(() -> {
                // 멤버 4 또는 8이 없으면 첫 번째 멤버 사용
                return allMembers.stream()
                    .findFirst()
                    .orElseGet(() -> {
                        // 멤버가 없으면 기본 멤버 생성
                        Member newMember = Member.builder()
                            .name("테스트 회원")
                            .target(Target.BULK)
                            .physicalInfo("{\"height\": 175, \"weight\": 70}")
                            .build();
                        return memberRepository.save(newMember);
                    });
            });
        
        MemberResponse response = MemberResponse.builder()
            .id(member.getId())
            .name(member.getName())
            .target(member.getTarget() != null ? member.getTarget().name() : null)
            .physicalInfo(member.getPhysicalInfo())
            .build();
        
        return ResponseEntity.ok(response);
    }
}

