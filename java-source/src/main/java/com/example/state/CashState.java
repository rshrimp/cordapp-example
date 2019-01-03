package com.example.state;


import net.corda.core.contracts.Amount;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.NullKeys;
import net.corda.core.identity.AbstractParty;

import java.security.PublicKey;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;

// *********
// * State *
// *********
public class CashState implements LinearState {

    private final Amount<Currency> issuedAmount;
    private final AbstractParty bank;
    private final AbstractParty borrower;
    private final UniqueIdentifier linearId;

    /* --- Constructors  --- */

    public CashState(Amount<Currency> amount, AbstractParty bank, AbstractParty borrower, UniqueIdentifier linearId) {
        this.issuedAmount = amount;
        this.bank = bank;
        this.borrower = borrower;
        this.linearId = linearId;
    }

    //without linearId
    public CashState(Amount<Currency> amount, AbstractParty bank, AbstractParty borrower) {
        this.issuedAmount = amount;
        this.bank = bank;
        this.borrower = borrower;
        this.linearId = new UniqueIdentifier();
    }


    /*  --- Getters ---*/
    public Amount<Currency> getAmount() {
        return issuedAmount;
    }

    public AbstractParty getBank() {
        return bank;
    }

    public AbstractParty getBorrower() {
        return borrower;
    }

    public UniqueIdentifier getLinearId() {
        return linearId;
    }
    /*  ---  override equals and hashcode, get participants  ---*/

    @Override
    public List<AbstractParty> getParticipants() {
        return Arrays.asList(bank, borrower);
    }

    public List<PublicKey> getParticipantKeys() {
        return getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList());
    }

    @Override
    public int hashCode() {
        return Objects.hash(issuedAmount, bank, borrower, linearId);
    }

    @Override
    public boolean equals(Object obj) {

        if (obj instanceof CashState) {
            CashState tmp = (CashState) obj;

            return tmp.borrower.equals(this.borrower) &&
                    tmp.bank.equals(this.bank) &&
                    tmp.issuedAmount.equals(this.issuedAmount) &&
                    tmp.linearId.equals(this.linearId);

        }
        return false;

    }

    /* --- utility functions for cash transfer ----*/
    public CashState withNewBorrower(AbstractParty newBorrower) {
        return new CashState(this.issuedAmount, this.bank, newBorrower, this.linearId);
    }

    public CashState withoutBorrower() {
        return new CashState(this.issuedAmount, this.bank, NullKeys.INSTANCE.getNULL_PARTY(), this.linearId);
    }

    /* --- utility function to get the keys --- */
    private Set<PublicKey> keysFromParticipants(CashState state) {
        return state
                .getParticipants().stream()
                .map(AbstractParty::getOwningKey)
                .collect(toSet());
    }

    @Override
    public String toString() {
        return String.format("CashState(amount=%s, lender=%s, borrower=%s, linearId=%s)", issuedAmount, bank, borrower, linearId);
    }

}