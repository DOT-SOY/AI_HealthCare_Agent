package com.backend.dto.order.request;

import com.backend.domain.order.OrderStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class OrderListRequest {

    @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다")
    private int page = 1;

    @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
    @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
    private int pageSize = 20;

    private String sortBy = "createdAt";
    private Sort.Direction direction = Sort.Direction.DESC;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    private OrderStatus status;

    /** Query param binding: page_size -> pageSize */
    public void setPage_size(int pageSize) {
        this.pageSize = pageSize;
    }

    /** Query param binding: from_date -> fromDate */
    public void setFrom_date(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    /** Query param binding: to_date -> toDate */
    public void setTo_date(LocalDate toDate) {
        this.toDate = toDate;
    }

    public org.springframework.data.domain.Pageable toPageable() {
        return org.springframework.data.domain.PageRequest.of(
                page - 1,
                pageSize,
                Sort.by(direction, sortBy)
        );
    }
}
