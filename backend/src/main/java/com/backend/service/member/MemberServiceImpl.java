package com.backend.service.member;

import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.domain.member.Member;
import com.backend.dto.member.MemberDTO;
import com.backend.dto.member.MemberModifyDTO;
import com.backend.dto.memberinfo.MemberInfoBodyDTO;
import com.backend.service.memberinfo.MemberInfoBodyService;
import com.backend.repository.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional // 트랜잭션 처리 (오류 발생 시 롤백)
@Log4j2        // 로그 기록용
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberInfoBodyService memberInfoBodyService;

    @Override
    public Long join(MemberDTO memberDTO) {
        // 간단 로깅 (향후 SecurityLogUtil 도입 시 교체 가능)
        log.info("회원가입 요청: {}", memberDTO.getEmail());

        // 1. 중복 이메일 검증
        validateDuplicateMember(memberDTO.getEmail());

        // 2. DTO -> Entity 변환 (비밀번호 암호화 포함)
        Member member = dtoToEntity(memberDTO, passwordEncoder);

        // 3. DB 저장
        memberRepository.save(member);

        // 4. 회원가입 시점의 기본 신체 정보 저장 (member_info_body)
        MemberInfoBodyDTO bodyDto = MemberInfoBodyDTO.builder()
                .height(memberDTO.getHeight() != null ? memberDTO.getHeight().doubleValue() : null)
                .weight(memberDTO.getWeight())
                .build();
        memberInfoBodyService.create(member.getId(), bodyDto);

        return member.getId();
    }

    // 중복 검사 로직 (탈퇴 회원 제외, existsById 패턴처럼 존재 여부만 확인)
    private void validateDuplicateMember(String email) {
        if (memberRepository.existsByEmailAndIsDeletedFalse(email)) {
            throw new BusinessException(ErrorCode.MEMBER_DUPLICATE_EMAIL);
        }
    }

    @Override
    public void withdraw(String email) {
        log.info("회원 탈퇴 요청: {}", email);

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("회원 정보를 찾을 수 없습니다."));

        // 이미 탈퇴한 회원인지 체크
        if (member.isDeleted()) {
            throw new IllegalStateException("이미 탈퇴한 회원입니다.");
        }

        // 논리 삭제 처리
        member.changeDeleted(true);
        // JPA 영속 상태이므로 트랜잭션 커밋 시 자동으로 UPDATE 됨

        log.info("회원 탈퇴 완료: {}", email);
    }

    @Override
    public void modify(String email, MemberModifyDTO memberModifyDTO) {
        log.info("회원 정보 수정 요청: {}", email);

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("회원 정보를 찾을 수 없습니다."));

        if (member.isDeleted()) {
            throw new IllegalStateException("탈퇴된 계정입니다.");
        }

        member.setName(memberModifyDTO.getName());
        member.setGender(Member.Gender.valueOf(memberModifyDTO.getGender()));
        member.setBirthDate(LocalDate.parse(memberModifyDTO.getBirthDate()));
        member.setHeight(memberModifyDTO.getHeight());
        member.setWeight(memberModifyDTO.getWeight());
        member.changePw(passwordEncoder.encode(memberModifyDTO.getPw()));

        // 영속 상태라 save 호출 없이도 반영되지만, 명시적으로 남김
        memberRepository.save(member);

        log.info("회원 정보 수정 완료: {}", email);
    }
}