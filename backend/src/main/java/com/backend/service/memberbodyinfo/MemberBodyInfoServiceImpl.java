package com.backend.service.memberbodyinfo;

import com.backend.domain.memberbodyinfo.MemberBodyInfo;
import com.backend.domain.member.Member;
import com.backend.dto.memberbodyinfo.MemberBodyInfoRequestDto;
import com.backend.exception.ResourceNotFoundException;
import com.backend.repository.memberbodyinfo.MemberBodyInfoRepository;
import com.backend.repository.member.MemberRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;  

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberBodyInfoServiceImpl implements MemberBodyInfoService {
    
    private final MemberBodyInfoRepository memberBodyInfoRepository;
    private final MemberRepository memberRepository;
    
    @Override
    @Transactional
    public MemberBodyInfo create(MemberBodyInfoRequestDto requestDto) {
        // 회원 존재 확인 (memberId는 이메일로 처리)
        Member member = memberRepository.findByEmail(requestDto.getMemberId())
                .orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다. 이메일: " + requestDto.getMemberId()));
        
        // MemberBodyInfo 엔티티 생성
        MemberBodyInfo memberBodyInfo = new MemberBodyInfo();
        memberBodyInfo.setMember(member);
        memberBodyInfo.setHeight(requestDto.getHeight());
        memberBodyInfo.setWeight(requestDto.getWeight());
        memberBodyInfo.setMeasuredTime(requestDto.getMeasuredTime());
        memberBodyInfo.setBodyFatPercent(requestDto.getBodyFatPercent());
        memberBodyInfo.setSkeletalMuscleMass(requestDto.getSkeletalMuscleMass());
        memberBodyInfo.setNotes(requestDto.getNotes());
        memberBodyInfo.setPurpose(requestDto.getPurpose());
        
        // 저장
        return memberBodyInfoRepository.save(memberBodyInfo);
    }
    
    @Override
    public MemberBodyInfo findById(Long id) {
        MemberBodyInfo memberBodyInfo = memberBodyInfoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("신체 정보를 찾을 수 없습니다. ID: " + id));
        
        return memberBodyInfo;
    }
    
    @Override
    public List<MemberBodyInfo> findByMemberId(String memberId) {
        // memberId는 이메일로 처리
        // 회원 존재 확인
        Member member = memberRepository.findByEmail(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다. 이메일: " + memberId));
        
        return memberBodyInfoRepository.findByMember(member);
    }
    
    @Override
    public List<MemberBodyInfo> findAll() {
        return memberBodyInfoRepository.findAll();
    }

    @Override
    @Transactional
    public MemberBodyInfo update(Long id, MemberBodyInfoRequestDto requestDto) {
        MemberBodyInfo memberBodyInfo = memberBodyInfoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("신체 정보를 찾을 수 없습니다. ID: " + id));
        
        // 회원 변경 시 회원 존재 확인
        if (requestDto.getMemberId() != null && !requestDto.getMemberId().equals(memberBodyInfo.getMember().getEmail())) {
            Member member = memberRepository.findByEmail(requestDto.getMemberId())
                    .orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다. 이메일: " + requestDto.getMemberId()));
            memberBodyInfo.setMember(member);
        }
        
        // 필드 업데이트
        if (requestDto.getHeight() != null) {
            memberBodyInfo.setHeight(requestDto.getHeight());
        }
        if (requestDto.getWeight() != null) {
            memberBodyInfo.setWeight(requestDto.getWeight());
        }
        if (requestDto.getMeasuredTime() != null) {
            memberBodyInfo.setMeasuredTime(requestDto.getMeasuredTime());
        }
        if (requestDto.getBodyFatPercent() != null) {
            memberBodyInfo.setBodyFatPercent(requestDto.getBodyFatPercent());
        }
        if (requestDto.getSkeletalMuscleMass() != null) {
            memberBodyInfo.setSkeletalMuscleMass(requestDto.getSkeletalMuscleMass());
        }
        if (requestDto.getNotes() != null) {
            memberBodyInfo.setNotes(requestDto.getNotes());
        }
        if (requestDto.getPurpose() != null) {
            memberBodyInfo.setPurpose(requestDto.getPurpose());
        }
        
        return memberBodyInfo;
    }
    
    @Override
    @Transactional
    public void delete(Long id) {
        if (!memberBodyInfoRepository.existsById(id)) {
            throw new ResourceNotFoundException("신체 정보를 찾을 수 없습니다. ID: " + id);
        }
        
        memberBodyInfoRepository.deleteById(id);
    }
}
