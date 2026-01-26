package com.backend.util;


import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;

import javax.crypto.SecretKey;

import com.backend.common.exception.JWTException;
import com.backend.common.exception.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.InvalidClaimException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class JWTUtil {

    private static String key = "1234567890123456789012345678901234567890";

    // JWT 생성, valueMap → JWT payload(claims)에 담길 정보 (예: email, role), min → 토큰 만료 시간 (분 단위)
    public static String generateToken(Map<String, Object> valueMap, int min){
        return generateToken(valueMap, min * 60L);
    }

    // JWT 생성 (초 단위) - 테스트용
    public static String generateToken(Map<String, Object> valueMap, long seconds){
        SecretKey key = null;

        try{ // HMAC SHA256 방식으로 서명용 키 객체 생성, key.getBytes("UTF-8") → 문자열 키를 바이트로 변환
            key = Keys.hmacShaKeyFor(JWTUtil.key.getBytes("UTF-8"));

        }catch(Exception e){
            throw new RuntimeException(e.getMessage());
        }

        // JWT 빌드
        String jwtStr = Jwts.builder()
                .setHeader(Map.of("typ","JWT"))
                .setClaims(valueMap)
                .setIssuedAt(Date.from(ZonedDateTime.now().toInstant()))
                .setExpiration(Date.from(ZonedDateTime.now().plusSeconds(seconds).toInstant()))
                .signWith(key)
                .compact();

        return jwtStr;
    }
    // JWT 검증
    public static Map<String, Object> validateToken(String token) {

        Map<String, Object> claim = null;

        try{
            // SecretKey 재생성, 서명 검증을 위해 동일한 키 사용
            SecretKey key = Keys.hmacShaKeyFor(JWTUtil.key.getBytes("UTF-8"));
            // JWT 파싱 및 검증, 성공 시 JWT payload(claims)를 Map으로 반환
            claim = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token) // 서명 검증, payload 추출, 실패 시 에러
                    .getBody();

        }catch(MalformedJwtException malformedJwtException){
            throw new JWTException(ErrorCode.JWT_MALFORMED); // 잘못된 형식
        }catch(ExpiredJwtException expiredJwtException){
            throw new JWTException(ErrorCode.JWT_EXPIRED); // 만료된 토큰
        }catch(InvalidClaimException invalidClaimException){
            throw new JWTException(ErrorCode.JWT_INVALID); //payload(claims) 오류
        }catch(JwtException jwtException){
            throw new JWTException(ErrorCode.JWT_ERROR); // 기타 JWT 오류
        }catch(Exception e){
            throw new JWTException(ErrorCode.JWT_ERROR);
        }
        return claim;
    }

    /**
     * 만료된 토큰도 Claims를 반환 (refresh 시 기존 access claims 재사용 용도)
     * - 서명 검증은 수행
     * - 만료(Expired)인 경우에도 claims를 반환
     */
    public static Map<String, Object> getClaimsAllowExpired(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(JWTUtil.key.getBytes("UTF-8"));
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            // 만료되어도 claims는 반환
            return e.getClaims();
        } catch(MalformedJwtException malformedJwtException){
            throw new JWTException(ErrorCode.JWT_MALFORMED);
        } catch(InvalidClaimException invalidClaimException){
            throw new JWTException(ErrorCode.JWT_INVALID);
        } catch(JwtException jwtException){
            throw new JWTException(ErrorCode.JWT_ERROR);
        } catch(Exception e){
            throw new JWTException(ErrorCode.JWT_ERROR);
        }
    }

    // 토큰이 만료되었는지 확인하는 로직 (Refresh 로직에서 AccessToken 만료 확인용)
    public static boolean isExpired(String token) {
        try {
            validateToken(token);
        } catch (JWTException ex) {
            if (ex.getErrorCode() == ErrorCode.JWT_EXPIRED) {
                return true;
            }
        }
        return false;
    }

    // 만료 시간이 1시간 미만으로 남았는지 확인
    public static boolean checkTime(Integer exp) {
        // JWT exp를 날짜로 변환
        Date expDate = new Date((long) exp * (1000));
        // 현재 시간과의 차이 계산 - 밀리세컨즈
        long gap = expDate.getTime() - System.currentTimeMillis();
        // 분단위 계산
        long leftMin = gap / (1000 * 60);
        // 1시간도 안 남았는지 확인
        return leftMin < 60;
    }

}