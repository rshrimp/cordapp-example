package com.example.schema;

import com.example.state.CashState;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.Amount;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;
import net.corda.core.contracts.Amount;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Currency;
import java.util.UUID;

/**
 * An CashState schema.
 */
public class MyCashSchema1 extends MappedSchema {
    public MyCashSchema1() {
        super(MyCashSchema.class, 1, ImmutableList.of(PersistentCashState.class));
    }

    @Entity
    @Table(name = "cash_states")
    public static class PersistentCashState extends PersistentState {
        @Column(name = "bank") private final String bank;
        @Column(name = "borrower") private final String borrower;
        @Column(name = "issuedAmount") private final Amount<Currency> issuedAmount;
        @Column(name = "linear_id") private final UUID linearId;


        public PersistentCashState(String lender, String borrower, Amount<Currency>  value, UUID linearId) {
            this.bank = lender;
            this.borrower = borrower;
            this.issuedAmount = value;
            this.linearId = linearId;
        }

        // Default constructor required by hibernate.
        public PersistentCashState() {

            this.bank = null;
            this.borrower = null;
            this.issuedAmount = new Amount(0, "");
            this.linearId = null;
        }

        public String getBank() {
            return bank;
        }

        public String getBorrower() {
            return borrower;
        }

        public Amount<Currency> getIssuedAmount() {
            return issuedAmount;
        }

        public UUID getLinearId() {
            return linearId;
        }
    }
}