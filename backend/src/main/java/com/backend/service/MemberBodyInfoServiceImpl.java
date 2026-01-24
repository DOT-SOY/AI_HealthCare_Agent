package com.backend.service;

import com.backend.dto.MemberBodyInfoRequestDto;
import com.backend.dto.MemberBodyInfoResponseDto;
import com.backend.entity.Member;
import com.backend.entity.MemberBodyInfo;
import com.backend.exception.ResourceNotFoundException;
import com.backend.repository.MemberBodyInfoRepository;
import com.backend.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberBodyInfoServiceImpl implements MemberBodyInfoService {
    
    private final MemberBodyInfoRepository memberBodyInfoRepository;
    private final MemberRepository memberRepository;
    
    @Override
    @Transactional
    public MemberBodyInfoResponseDto create(MemberBodyInfoRequestDto requestDto) {
        // 회원 존재 확인
        Member member = memberRepository.findById(requestDto.getMemberId())
                .orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다. ID: " + requestDto.getMemberId()));
        
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
        MemberBodyInfo saved = memberBodyInfoRepository.save(memberBodyInfo);
        
        return MemberBodyInfoResponseDto.from(saved);
    }
    
    @Override
    public MemberBodyInfoResponseDto findById(Long id) {
        MemberBodyInfo memberBodyInfo = memberBodyInfoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("신체 정보를 찾을 수 없습니다. ID: " + id));
        
        return MemberBodyInfoResponseDto.from(memberBodyInfo);
    }
    
    @Override
    public List<MemberBodyInfoResponseDto> findByMemberId(String memberId) {
        // 회원 존재 확인
        if (!memberRepository.existsById(memberId)) {
            throw new ResourceNotFoundException("회원을 찾을 수 없습니다. ID: " + memberId);
        }
        
        List<MemberBodyInfo> bodyInfos = memberBodyInfoRepository.findByMemberId(memberId);
        return bodyInfos.stream()
                .map(MemberBodyInfoResponseDto::from)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<MemberBodyInfoResponseDto> findAll() {
        List<MemberBodyInfo> bodyInfos = memberBodyInfoRepository.findAll();
        return bodyInfos.stream()
                .map(MemberBodyInfoResponseDto::from)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public MemberBodyInfoResponseDto update(Long id, MemberBodyInfoRequestDto requestDto) {
        MemberBodyInfo memberBodyInfo = memberBodyInfoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("신체 정보를 찾을 수 없습니다. ID: " + id));
        
        // 회원 변경 시 회원 존재 확인
        if (requestDto.getMemberId() != null && !requestDto.getMemberId().equals(memberBodyInfo.getMember().getId())) {
            Member member = memberRepository.findById(requestDto.getMemberId())
                    .orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다. ID: " + requestDto.getMemberId()));
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
        
        return MemberBodyInfoResponseDto.from(memberBodyInfo);
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
