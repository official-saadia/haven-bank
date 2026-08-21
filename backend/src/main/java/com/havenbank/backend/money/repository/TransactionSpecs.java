package com.havenbank.backend.money.repository;

import com.havenbank.backend.money.domain.Transaction;
import com.havenbank.backend.money.domain.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Predicates for the filterable transaction history (FR-4.2).
 *
 * <h2>Why not {@code (:param is null or column = :param)} in a @Query</h2>
 * That pattern reads well and breaks on PostgreSQL. A bare {@code ?} inside {@code ? is null} sits
 * in no typed context, so the server cannot infer the parameter's type and fails the prepared
 * statement with <em>could not determine data type of parameter $n</em>. It is also bad for the
 * query plan even where it does run: the {@code OR} branch makes the predicate non-sargable, so the
 * index on {@code (source_account_id, created_at)} stops being usable and the scan degrades as the
 * table grows — the opposite of NFR-3.5.
 *
 * <p>Building the predicate from only the filters actually supplied avoids both problems: every
 * parameter appears in a typed comparison, and an absent filter contributes no SQL at all.
 */
public final class TransactionSpecs {

    private TransactionSpecs() {
    }

    /**
     * Both legs of the account's history: money sent from it and money received by it.
     */
    public static Specification<Transaction> touchingAccount(UUID accountId) {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("sourceAccountId"), accountId),
                cb.equal(root.get("destinationAccountId"), accountId));
    }

    public static Specification<Transaction> ofType(TransactionType type) {
        return type == null ? null : (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Transaction> createdFrom(Instant from) {
        return from == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Transaction> createdUntil(Instant to) {
        return to == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    public static Specification<Transaction> amountAtLeast(BigDecimal min) {
        return min == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), min);
    }

    public static Specification<Transaction> amountAtMost(BigDecimal max) {
        return max == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("amount"), max);
    }

    /**
     * Combines only the filters that were actually supplied.
     *
     * <p>Note {@code Specification.allOf} cannot be used here: it reduces with {@code and()}, which
     * asserts each argument is non-null and throws {@code IllegalArgumentException} on the first
     * absent filter. The nulls have to be stripped before the reduction, not during it.
     */
    public static Specification<Transaction> history(UUID accountId, TransactionType type,
                                                     Instant from, Instant to,
                                                     BigDecimal minAmount, BigDecimal maxAmount) {
        return combine(
                touchingAccount(accountId),
                ofType(type),
                createdFrom(from),
                createdUntil(to),
                amountAtLeast(minAmount),
                amountAtMost(maxAmount));
    }

    @SafeVarargs
    private static Specification<Transaction> combine(Specification<Transaction>... specs) {
        return Arrays.stream(specs)
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                // touchingAccount is never null, so there is always at least one predicate.
                .orElseThrow(() -> new IllegalStateException("no predicates supplied"));
    }
}