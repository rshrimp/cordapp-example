package com.example.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.example.contract.IOUContract;
import com.example.state.IOUState;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;


import java.util.List;


/**
 * This flow allows two parties (the [Initiator] and the [Acceptor]) to come to an agreement about the IOU encapsulated
 * within an [IOUState] to cancel an IOU upon fulfilling the obligation.
 * <p>
 * <p>
 * All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable annotation.
 */
public class IOUDestroyerFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class Destroyer extends FlowLogic<SignedTransaction> {

        public UniqueIdentifier linearId;

        public Destroyer(UniqueIdentifier linearId) {
            this.linearId = linearId;
        }

        private final Step GENERATING_CANCEL_QUERY_TRANSACTION = new Step("Generating cancel query transaction based on existing IOU.");
        private final Step GENERATING_CANCEL_TRANSACTION = new Step("Generating cancel transaction based on existing IOU.");
        private final Step VERIFYING_CANCEL_TRANSACTION = new Step("Verifying cancel contract constraints.");
        private final Step SIGNING_CANCEL_TRANSACTION = new Step("Signing cancel transaction with our private key.");

        private final Step FINALISING_CANCEL_TRANSACTION = new Step("Obtaining notary signature and recording transaction for cancel transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        // The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
        // checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call()
        // function.
        private final ProgressTracker progressTracker = new ProgressTracker(
                GENERATING_CANCEL_TRANSACTION,
                VERIFYING_CANCEL_TRANSACTION,
                SIGNING_CANCEL_TRANSACTION,
                FINALISING_CANCEL_TRANSACTION
        );


        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Obtain a reference to the notary we want to use.
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);


            //step 1.
            progressTracker.setCurrentStep(GENERATING_CANCEL_QUERY_TRANSACTION);
            // Retrieve the state using its linear ID.
            QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(
                    null,
                    ImmutableList.of(linearId),
                    Vault.StateStatus.UNCONSUMED,
                    null);

            List<StateAndRef<IOUState>> iouStates = getServiceHub().getVaultService().queryBy(IOUState.class, queryCriteria).getStates();
            if (iouStates.size() != 1) {
                throw new FlowException(String.format("Obligation with id %s not found.", linearId));
            }
            //get the state from the vault
            StateAndRef<IOUState> inputStateAndRef = iouStates.get(0);


            // Stage 2.
            progressTracker.setCurrentStep(GENERATING_CANCEL_TRANSACTION);
            // Generate an unsigned transaction.
            Party me = getOurIdentity();
            IOUState iouState = iouStates.get(0).getState().getData();

            final Command<IOUContract.Commands.Destroy> txCommand = new Command<>(
                    new IOUContract.Commands.Destroy(),
                    ImmutableList.of(iouState.getLender().getOwningKey()));


            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addInputState(inputStateAndRef)
                    .addCommand(txCommand);

            // Stage 3.
            progressTracker.setCurrentStep(VERIFYING_CANCEL_TRANSACTION);
            // Verify that the transaction is valid.
            txBuilder.verify(getServiceHub());

            // Stage 4.
            progressTracker.setCurrentStep(SIGNING_CANCEL_TRANSACTION);
            // Sign the transaction.
            final SignedTransaction signedTx = getServiceHub().signInitialTransaction(txBuilder);


            // Stage 5.
            progressTracker.setCurrentStep(FINALISING_CANCEL_TRANSACTION);
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(new FinalityFlow(signedTx));
        }
    }


}



