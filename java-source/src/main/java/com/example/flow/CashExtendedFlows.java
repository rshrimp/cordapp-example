package com.example.flow;


import co.paralleluniverse.fibers.Suspendable;
import com.example.contract.CashContract;
import com.example.state.CashState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;

import java.security.PublicKey;
import java.util.Currency;
import java.util.List;

public class CashExtendedFlows {
    /* --------------------- IssueCashFlow  ------------------------------------------------------------------------- */
    @InitiatingFlow
    @StartableByRPC
    public static class IssueCashFlow extends CashFlow {

        private final Amount<Currency> issuedAmount;
        private final Party bank;

        /* --- Constructor -------------------------------------------------------------- */
        public IssueCashFlow(Amount<Currency> issuedAmount, Party bank) {
            this.issuedAmount = issuedAmount;
            this.bank = bank;

        }

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            /* --- Start  -------------------------------------------------- */
            // Step 1. GENERATING_TRANSACTION.
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            final CashState cashState = new CashState(issuedAmount, bank, getOurIdentity());
            final PublicKey callerSigningKey = cashState.getBorrower().getOwningKey();

            // Step 2. VERIFYING_TRANSACTION.
            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            final List<PublicKey> requiredSigners = cashState.getParticipantKeys();

            final TransactionBuilder utx = new TransactionBuilder(getAvailableNotary())//Obtain a reference to the notary we want to use.
                    .addOutputState(cashState, CashContract.ID)
                    .addCommand(new CashContract.Commands.IssueCash(), requiredSigners);


            // Step 3. SIGNING_TRANSACTION.
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            final SignedTransaction ptx = getServiceHub().signInitialTransaction(utx, callerSigningKey);

            // Step 4. GATHERING_SIGS.
            progressTracker.setCurrentStep(GATHERING_SIGS);
            final FlowSession lenderFlow = initiateFlow(bank);
            final SignedTransaction stx = subFlow(new CollectSignaturesFlow(
                    ptx,
                    ImmutableSet.of(lenderFlow),
                    ImmutableList.of(callerSigningKey),
                    GATHERING_SIGS.childProgressTracker())
            );

            // Step 5. FINALISING_TRANSACTION.
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            return subFlow(new FinalityFlow(stx, FINALISING_TRANSACTION.childProgressTracker()));
        }
    }


    /* ---------------- TransferCash from one borrower to another borrower (essentially changing hands initiated by bank only---- */
    @StartableByRPC
    @InitiatingFlow
    public static class TransferCash extends CashFlow {


        private final UniqueIdentifier linearId;
        private final Party newBorrower;

        public TransferCash(UniqueIdentifier linearId, Party newBorrower) {
            this.linearId = linearId;
            this.newBorrower = newBorrower;
        }

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {

            /* --- Start  -------------------------------------------------- */
            // Step 1. GENERATING_TRANSACTION.

            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            //get cashState passed from the vault using linearId
            final StateAndRef<CashState> cashStateRef = getCashStateByLinearId(linearId);
            final CashState inputCashState = cashStateRef.getState().getData();

            //only current lender can transfer it to the new borrower and can never be by the current borrower
            final AbstractParty bankIdentity = inputCashState.getBank();
            if (!getOurIdentity().equals(bankIdentity))
                throw new IllegalStateException("Transfer can only be initiated by the bank");

            //generate new output cashState
            final CashState newCashState = inputCashState.withNewBorrower(newBorrower);

            // Step 2. VERIFYING_TRANSACTION.

            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            //we need signatures from bank, old borrower and new borrower

            final List<PublicKey> requiredSigners = new ImmutableList.Builder<PublicKey>()
                    .addAll(inputCashState.getParticipantKeys())
                    .add(newCashState.getBorrower().getOwningKey()).build();

            final TransactionBuilder builder = new TransactionBuilder(getAvailableNotary())//Obtain a reference to the notary we want to use.
                    .addOutputState(newCashState, CashContract.ID)
                    .addInputState(cashStateRef)
                    .addCommand(new CashContract.Commands.TransferCash(), requiredSigners);

            // Step 3. SIGNING_TRANSACTION.
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            builder.verify(getServiceHub());
            final SignedTransaction signedTx = getServiceHub().signInitialTransaction(builder);


            // Step 5. FINALISING_TRANSACTION.
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            return subFlow(new FinalityFlow(signedTx, FINALISING_TRANSACTION.childProgressTracker()));

        }

    }


    /* --------------------- DestroyCash to Bank--------------------------------------------- */
    @StartableByRPC
    @InitiatingFlow
    public static class DetroyCash extends CashFlow {


        private final UniqueIdentifier linearId;


        public DetroyCash(UniqueIdentifier linearId, Amount<Currency> amount) {
            this.linearId = linearId;

        }

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {

            /* --- Start  -------------------------------------------------- */
            // Step 1. GENERATING_TRANSACTION.

            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            //get cashState passed from the vault using linearId
            final StateAndRef<CashState> cashStateRef = getCashStateByLinearId(linearId);
            final CashState inputCashState = cashStateRef.getState().getData();

            //only  lender can destroy/make cash disappear, obviously
            final AbstractParty bankIdentity = inputCashState.getBank();
            final AbstractParty borrowerIdentity = inputCashState.getBorrower();
            if (!getOurIdentity().equals(bankIdentity))
                throw new IllegalStateException("Cash can only be destroyed by the bank");

            // Step 2. VERIFYING_TRANSACTION.

            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            //we need signatures from bank and notary only when destroying cash

            final TransactionBuilder builder = new TransactionBuilder(getAvailableNotary())//Obtain a reference to the notary we want to use.
                    .addInputState(cashStateRef)
                    .addCommand(new CashContract.Commands.TransferCash(), ImmutableList.of(inputCashState.getBank().getOwningKey()));

            builder.verify(getServiceHub());

            // Step 3. SIGNING_TRANSACTION.
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            final SignedTransaction signedTx = getServiceHub().signInitialTransaction(builder);


            // Step 5. FINALISING_TRANSACTION.
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            return subFlow(new FinalityFlow(signedTx, FINALISING_TRANSACTION.childProgressTracker()));


        }

    }


}
