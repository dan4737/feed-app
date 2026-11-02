package com.bptn.feedApp.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;

/* A trimmed-down copy of Spring's Page object. Page carries a lot of internal
   detail the front end does not need, so we copy out only the four values it does.
   T is whatever is being paged, for example Feed. */
public class PageResponse<T> {

    int pageNum;
    int pageSize;
    int totalPages;
    List<T> content;

    public PageResponse(Page<T> paged) {

        if (Optional.ofNullable(paged).isPresent()) {
            this.pageNum = paged.getNumber();
            this.pageSize = paged.getSize();
            this.totalPages = paged.getTotalPages();
            this.content = paged.getContent();
        }
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
    }

}
