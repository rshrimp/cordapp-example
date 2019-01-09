package com.example.contract;

import com.example.state.IOUState;
import net.corda.core.contracts.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.LedgerTransaction;

import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

/**
 * A implementation of a basic smart contract in Corda.
 * <p>
 * This contract enforces rules regarding the creation of a valid [IOUState], which in turn encapsulates an [IOU].
 * <p>
 * For a new [IOU] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [IOU].
 * - An Create() command with the public keys of both the lender and the borrower.
 * <p>
 * All contracts must sub-class the [Contract] interface.
 */
public class IOUContract implements Contract {
    public static final String IOU_CONTRACT_ID = "com.example.contract.IOUContract";

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    @Override
    public void verify(LedgerTransaction tx) {

        //get the command issued - it has to be either create or destroy type
        Command command = tx.getCommand(0);

        if (command.getValue() instanceof Commands.Create) {
            final CommandWithParties<Commands.Create> createCommand = requireSingleCommand(tx.getCommands(), Commands.Create.class);
            requireThat(require -> {
                // Generic constraints around the IOU transaction.
                require.using("No inputs should be consumed when issuing an IOU.",
                        tx.getInputs().isEmpty());
                require.using("Only one output state should be created.",
                        tx.getOutputs().size() == 1);
                final IOUState out = tx.outputsOfType(IOUState.class).get(0);
                require.using("The lender and the borrower cannot be the same entity.",
                        out.getLender() != out.getBorrower());
                require.using("All of the participants must be signers.",
                        createCommand.getSigners().containsAll(out.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList())));

                // IOU-specific constraints.
                require.using("The IOU's value must be non-negative.",
                        out.getValue() > 0);

                return null;
            });
        } else if (command.getValue() instanceof Commands.Destroy) {
            final CommandWithParties<Commands.Destroy> destroyCommand = requireSingleCommand(tx.getCommands(), Commands.Destroy.class);
            //List<PublicKey> requiredSigners = command.getSigners();
            requireThat(require -> {
                // Generic constraints around the IOU transaction.
                require.using("Only one input should be consumed when destroying/closing an IOU.",
                        tx.getInputs().size() == 1);
                require.using("No output state should be created.",
                        tx.getOutputs().isEmpty());
                require.using("Input must be IOUState type",
                        tx.getInput(0) instanceof IOUState);

                ContractState input = tx.getInput(0);
                IOUState inputIOU = (IOUState) input;

                Party iouLender = inputIOU.getLender();
                require.using("IOU Lender must be  the signer for closing/destroying IOUState",
                        destroyCommand.getSigners().contains(inputIOU.getLender().getOwningKey()));
                return null;
            });
        } else {
            throw new IllegalArgumentException(" Unknown command is provided...");
        }


    }

    /**
     * This contract only implements  command, Create.
     */
    public interface Commands extends CommandData {
        class Create implements Commands {
        }

        //add command to Destroy IOU after it is repaid
        class Destroy implements Commands {
        }
    }


}