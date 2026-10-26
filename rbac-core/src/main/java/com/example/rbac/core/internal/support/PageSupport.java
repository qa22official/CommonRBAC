package com.example.rbac.core.internal.support;

import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;

/**
 * Normalizes page/size input. Exceeding the size limit is an explicit error,
 * never silent truncation (00 §5).
 */
public final class PageSupport {

    public static final int MAX_SIZE = 200;

    private PageSupport() {
    }

    public static PageSpec normalize(int page, int size) {
        int safePage = page < 1 ? 1 : page;
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCodes.PARAM_INVALID,
                    "size 必须在 1 到 " + MAX_SIZE + " 之间");
        }
        long offset = (long) (safePage - 1) * size;
        return new PageSpec(safePage, size, (int) Math.min(offset, Integer.MAX_VALUE));
    }

    public record PageSpec(int page, int size, int offset) {
    }
}
