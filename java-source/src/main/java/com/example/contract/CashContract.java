package com.example.contract;


import com.example.state.CashState;
import com.google.common.collect.Sets;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.LedgerTransaction;

import java.security.PublicKey;
import java.util.HashSet;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;


// ************
// * CashContract *
// ************
public class CashContract implements Contract {
    // This is used to identify our contract when building a transaction.
    public static final String ID = "com.bank.CashContract";


    private Set<PublicKey> keysFromParticipants(CashState cash) {
        return cash
                .getParticipants().stream()
                .map(AbstractParty::getOwningKey)
                .collect(toSet());

    }

    /* -------------------------------------------------------------------------------------------------
       verify : A transaction is valid if the verify() function of the contract of all the transaction's
       input and output states does not throw an exception
     --------------------------------------------------------------------------------------------------- */
    @Override
    public void verify(LedgerTransaction tx) {

        // get the command to be processed
        final CommandWithParties<Commands> command = requireSingleCommand(tx.getCommands(), Commands.class);
        final Commands commandData = command.getValue();
        final Set<PublicKey> setOfSigners = new HashSet<>(command.getSigners());

        //check the command type and fork
        if (commandData instanceof Commands.IssueCash) {
            issueCash(tx, setOfSigners);
        } else if (commandData instanceof Commands.TransferCash) {
            transferCash(tx, setOfSigners);
        } else if (commandData instanceof Commands.DestroyCash) {
            destroyCash(tx, setOfSigners);
        } else {
            throw new IllegalArgumentException("Command has to be one of issue/transfer/destroy.");
        }
    }

    /* -----------------------------------------------------------------------
                    Processing of cash issuance
       ----------------------------------------------------------------------- */
    private void issueCash(LedgerTransaction tx, Set<PublicKey> signers) {
        requireThat(req -> {
            req.using("No inputs should be consumed when issuing Cash.",
                    tx.getInputStates().isEmpty());
            req.using("Only one cash state should be created when issuing Cash.", tx.getOutputStates().size() == 1);
            CashState cashState = (CashState) tx.getOutputStates().get(0);
            req.using("A newly issued cash must have a positive amount.", cashState.getAmount().getQuantity() > 0);
            req.using("The lender and borrower cannot be the same identity.", !cashState.getBorrower().equals(cashState.getBank()));
            req.using("Both lender and borrower together only may sign cash issue transaction.",
                    signers.equals(keysFromParticipants(cashState)));
            return null;
        });
    }

    /* -----------------------------------------------------------------------
                    Processing of transferring/moving cash
       ----------------------------------------------------------------------- */
    private void transferCash(LedgerTransaction tx, Set<PublicKey> signers) {
        requireThat(req -> {
            req.using("A cash transfer transaction should only consume one input state.", tx.getInputs().size() == 1);
            req.using("A cash transfer transaction should only create one output state.", tx.getOutputs().size() == 1);
            CashState input = tx.inputsOfType(CashState.class).get(0);
            CashState output = tx.outputsOfType(CashState.class).get(0);
            req.using("Only the borrower property may change during cash transfer.", input.withoutBorrower().equals(output.withoutBorrower()));
            req.using("The lender must NOT change.", input.getBank().equals(output.getBank()));
            req.using("The lender, old  and new borrower only must sign CashState transfer transaction",
                    signers.equals(Sets.union(keysFromParticipants(input), keysFromParticipants(output))));
            return null;
        });

    }

    /* -----------------------------------------------------------------------
                Processing of destroying cash
       ----------------------------------------------------------------------- */
    private void destroyCash(LedgerTransaction tx, Set<PublicKey> signers) {

        requireThat(req -> {
            req.using("A cash destroy transaction should only consume one input state.", tx.getInputs().size() == 1);
            req.using("A cash destroy transaction should NOT create any output state.", tx.getOutputs().size() == 0);
            CashState input = tx.inputsOfType(CashState.class).get(0);
            req.using("The lender must sign CashState destroy transaction",
                    signers.equals(input.getBank().getOwningKey()));
            return null;
        });

    }



    /* -----------------------------------------------------------------------
            Commands that are Used to indicate the transaction's intent.
    ----------------------------------------------------------------------- */

    public interface Commands extends CommandData {
        class IssueCash implements Commands {
        }

        class TransferCash implements Commands {
        }

        class DestroyCash implements Commands {
        }
    }

    public static class IssueCash implements CommandData {
    }

    public static class TransferCash implements CommandData {
    }

    public static class DestroyCash implements CommandData {
    }

}