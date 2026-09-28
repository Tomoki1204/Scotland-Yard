package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import java.util.*;
import java.util.stream.Collectors;

import uk.ac.bris.cs.scotlandyard.model.Move.*;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;

/**
 * cw-model
 * Stage 1: Complete this class
 */
public final class MyGameStateFactory implements Factory<GameState> {

    @Nonnull @Override public GameState build(GameSetup setup, Player mrX, ImmutableList<Player> detectives) {

        ImmutableSet.Builder<Piece> buildPlayers = ImmutableSet.builder();
        buildPlayers.add(mrX.piece());
        for (Player x : detectives) {
            buildPlayers.add(x.piece());
        }
        ImmutableSet<Piece> players = buildPlayers.build();

        ImmutableSet<Piece> startingTurn = ImmutableSet.of(mrX.piece());

        final class MyGameState implements GameState {

            private final GameSetup setup;
            private ImmutableSet<Piece> remaining;
            private ImmutableList<LogEntry> log;
            private Player mrX;
            private List<Player> detectives;
            private ImmutableSet<Move> moves;
            private ImmutableSet<Piece> winner;


            private MyGameState(
                    final GameSetup setup,
                    final ImmutableSet<Piece> remaining,
                    final ImmutableList<LogEntry> log,
                    final Player mrX,
                    final List<Player> detectives) {
                this.setup = setup;
                this.remaining = remaining;
                this.log = log;
                this.mrX = mrX;
                this.detectives = detectives;
                this.moves = getAvailableMoves();

                if(setup.moves.isEmpty()) throw new IllegalArgumentException("Moves is empty");

                if (mrX.piece() == null) {
                    throw new NullPointerException("Mr X is not assigned");
                } else if (detectives.isEmpty()) {
                    throw new NullPointerException("Detectives are not assigned");
                } else if (mrX.isDetective()) {
                    throw new IllegalArgumentException("Mr X cannot be a detective");
                } else if (setup == null) {
                    throw new NullPointerException("Game setup is null");
                } else if (!mrX.isMrX()) {
                    throw new IllegalArgumentException("Mr X is not Mr X");
                } else if (setup.graph.nodes().isEmpty()) {
                    throw new IllegalArgumentException("Graph is empty");
                }

                Set<Piece> colourCounter = new HashSet<>();
                Set<Integer> locations = new HashSet<>();

                for (Player i : detectives) {
                    int location = i.location();
                    if (i.isMrX()) {
                        throw new IllegalArgumentException("Detective is a Mr X");
                    } else if (colourCounter.contains(i.piece())) {
                        throw new IllegalArgumentException("Multiple same colour detectives");
                    }
                    colourCounter.add(i.piece());
                    if (i.has(ScotlandYard.Ticket.SECRET)) {
                        throw new IllegalArgumentException("Detective has a secret ticket");
                    }
                    if (i.has(ScotlandYard.Ticket.DOUBLE)) {
                        throw new IllegalArgumentException("Detective has a double ticket");
                    }
                    if (locations.contains(location)) {
                        throw new IllegalArgumentException("Two players cannot be in the same location");
                    }
                    locations.add(location);
                }
            }

            public Ticket currentTicketUse(Player player) {
                for (Transport t : ScotlandYard.Transport.values()) {
                    if (player.has(t.requiredTicket())) {
                        return (t.requiredTicket());
                    }
                }
                throw new IllegalArgumentException("No tickets can be used");
            }

            public Player executeMove (Player player, Move move) {
                if (move instanceof Move.SingleMove singleMove) {
                    return player.use(singleMove.ticket).at(singleMove.destination);
                } else if (move instanceof Move.DoubleMove doubleMove) {
                    return player.use(Ticket.DOUBLE).use(doubleMove.ticket1).use(doubleMove.ticket2).at(doubleMove.destination2);
                } else throw new IllegalArgumentException("Unknown move");
            }

            @Nonnull
            @Override
            public GameState advance(Move move) {
                if(!moves.contains(move)) throw new IllegalArgumentException("Illegal move: "+move);
                Piece currentPlayer = move.commencedBy();
                Set<Piece> newRemaining = new HashSet<>(remaining);

                if (currentPlayer.isMrX()) {
                    if (move instanceof SingleMove sm) {
                        LogEntry entry;
                        if (setup.moves.get(log.size())) {
                            entry = LogEntry.reveal(sm.ticket, sm.destination);
                        } else {
                            entry = LogEntry.hidden(sm.ticket);
                        }
                        mrX = executeMove(mrX, move);
                        log = ImmutableList.<LogEntry>builder().addAll(log).add(entry).build();
                        newRemaining = detectives.stream().map(Player::piece).collect(Collectors.toCollection(HashSet::new));
                    }
                    else if (move instanceof Move.DoubleMove dm) {
                        LogEntry entry1;
                        if (setup.moves.get(log.size())) {
                            entry1 = LogEntry.reveal(dm.ticket1, dm.destination1);
                        } else {
                            entry1 = LogEntry.hidden(dm.ticket1);
                        }

                        LogEntry entry2;
                        if (setup.moves.get(log.size() + 1)) {
                            entry2 = LogEntry.reveal(dm.ticket2, dm.destination2);
                        } else {
                            entry2 = LogEntry.hidden(dm.ticket2);
                        }
                        mrX = executeMove(mrX, move);
                        log = ImmutableList.<LogEntry>builder().addAll(log).add(entry1).add(entry2).build();
                        newRemaining = detectives.stream().map(Player::piece).collect(Collectors.toCollection(HashSet::new));
                    }
                }
                List<Player> updatedDetectives = new ArrayList<>();
                if (currentPlayer.isDetective()) {
                    for (Player detective : detectives) {
                        if (detective.piece().equals(currentPlayer)) {
                            if (detective.hasAtLeast(currentTicketUse(detective), 1)) {
                                Player updatedDetective = executeMove(detective, move);
                                mrX = mrX.give(currentTicketUse(detective));
                                newRemaining.remove(currentPlayer);
                                updatedDetectives.add(updatedDetective);
                            } else throw new IllegalArgumentException("Detective does not have the required ticket");
                        } else {
                            updatedDetectives.add(detective);
                        }
                    }
                    detectives = updatedDetectives;
                    if (newRemaining.isEmpty()) {
                        newRemaining = new HashSet<>();
                        newRemaining.add(mrX.piece());
                    }
                }
                return new MyGameState(setup, ImmutableSet.copyOf(newRemaining), log, mrX, detectives);
            }



            @Nonnull
            @Override
            public GameSetup getSetup() {
                return setup;
            }

            @Nonnull
            @Override
            public ImmutableSet<Piece> getPlayers() {
                return players;
            }

            @Nonnull
            @Override
            public Optional<Integer> getDetectiveLocation(Piece.Detective detective) {
                for (Player detect : detectives) {
                    if (detect.piece().equals(detective)) {
                        return Optional.of(detect.location());
                    }
                }
                return Optional.empty();
            }

            @Nonnull
            @Override
            public Optional<TicketBoard> getPlayerTickets(Piece piece) {
                if (piece == null) {
                    return Optional.empty();
                }
                if (mrX.piece().equals(piece)) {
                    return Optional.of(new Board.TicketBoard() {
                        @Override public int getCount(@Nonnull ScotlandYard.Ticket ticket) {
                            return mrX.tickets().getOrDefault(ticket, 0);
                        }
                    }
                    );
                }
                for (Player detective : detectives) {
                    if (detective.piece().equals(piece)) {
                        return Optional.of(new Board.TicketBoard() {
                            @Override public int getCount(@Nonnull ScotlandYard.Ticket ticket) {
                                return detective.tickets().getOrDefault(ticket, 0);
                            }
                        }
                        );
                    }
                }
                return Optional.empty();
            }

            @Nonnull
            @Override
            public ImmutableList<LogEntry> getMrXTravelLog() {
                return log;
            }


            @Nonnull
            @Override
            public ImmutableSet<Piece> getWinner() {
                Set<Piece> winners = new HashSet<>();

                for (Player detective : detectives) {
                    if (detective.location() == mrX.location()) {
                        for (Player d : detectives) {
                            winners.add(d.piece());
                        }
                        return ImmutableSet.copyOf(winners);
                    }
                }
                if (log.size() >= setup.moves.size()) {
                    winners.add(mrX.piece());
                    return ImmutableSet.copyOf(winners);
                }

                return ImmutableSet.of();
            }

            private static Set<SingleMove> makeSingleMoves(GameSetup setup, List<Player> detectives, Player player, int source){

                // TODO create an empty collection of some sort, say, HashSet, to store all the SingleMove we generate
                Set<Move.SingleMove> singleMove = new HashSet<>();

                for(int destination : setup.graph.adjacentNodes(source)) {
                    // TODO find out if destination is occupied by a detective
                    //  if the location is occupied, don't add to the collection of moves to return
//                    for (Player detective : detectives) {
//                        if (destination == detective.location()) {
//                            locationOverlap = true;
//                            break;
//                        }
//                    }
                    Set<Integer> detectiveLocations = detectives.stream().map(Player::location).collect(Collectors.toSet());
                    if (detectiveLocations.contains(destination)) {
                        continue;
                    }
                    for (Transport t : Objects.requireNonNull(setup.graph.edgeValueOrDefault(source, destination, ImmutableSet.of()))) {
                        // TODO find out if the player has the required tickets
                        //  if it does, construct a SingleMove and add it the collection of moves to return
                        if (player.tickets().getOrDefault(t.requiredTicket(), 0) > 0) {
                            singleMove.add(new Move.SingleMove(player.piece(), source, t.requiredTicket(), destination));

                        }
                    }
                    // TODO consider the rules of secret moves here
                    //  add moves to the destination via a secret ticket if there are any left with the player
                    if (player.tickets().getOrDefault(Ticket.SECRET, 0) > 0) {
                        singleMove.add(new Move.SingleMove(player.piece(), source, Ticket.SECRET, destination));
                    }

                }

                // TODO return the collection of moves
                return singleMove;
            }

            private Set<DoubleMove> makeDoubleMoves(GameSetup setup, List<Player> detectives, Player player, int source){
                Set<Move.DoubleMove> doubleMove = new HashSet<>();
                Set<Integer> detectiveLocations = detectives.stream().map(Player::location).collect(Collectors.toSet());
                if (setup.moves.size() - log.size() < 2) {
                    return doubleMove;
                }

                if (player.tickets().getOrDefault(Ticket.DOUBLE, 0) > 0) {
                    for (int firstDestination : setup.graph.adjacentNodes(source)) {

                        if (detectiveLocations.contains(firstDestination)) {
                            continue;
                        }

                        for (int finalDestination : setup.graph.adjacentNodes(firstDestination)) {

                            if (detectiveLocations.contains(finalDestination)) {
                                continue;
                            }

                            for (Transport t1 : Objects.requireNonNull(setup.graph.edgeValueOrDefault(source, firstDestination, ImmutableSet.of()))) {
                                Ticket ticket1 = t1.requiredTicket();
                                int ticket1Count = player.tickets().getOrDefault(ticket1, 0);

                                if (player.tickets().getOrDefault(ticket1, 0) > 0 || player.tickets().getOrDefault(Ticket.SECRET, 0) > 0) {

                                    for (Transport t2 : Objects.requireNonNull(setup.graph.edgeValueOrDefault(firstDestination, finalDestination, ImmutableSet.of()))) {
                                        Ticket ticket2 = t2.requiredTicket();
                                        int ticket2Count = player.tickets().getOrDefault(ticket2, 0);

                                        if ((ticket1.equals(ticket2) && ticket1Count > 1) || (!ticket1.equals(ticket2) && ticket1Count > 0 && ticket2Count > 0)) {
                                            doubleMove.add(new DoubleMove(player.piece(), source, ticket1, firstDestination, ticket2, finalDestination));
                                        }

                                        if (player.tickets().getOrDefault(Ticket.SECRET, 0) > 0) {
                                            if (ticket1Count > 0) {
                                                doubleMove.add(new DoubleMove(player.piece(), source, ticket1, firstDestination, Ticket.SECRET, finalDestination));
                                            }
                                            if (ticket2Count > 0) {
                                                doubleMove.add(new DoubleMove(player.piece(), source, Ticket.SECRET, firstDestination, ticket2, finalDestination));
                                            }
                                            doubleMove.add(new DoubleMove(player.piece(), source, Ticket.SECRET, firstDestination, Ticket.SECRET, finalDestination));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                return doubleMove;
            }

            @Nonnull
            @Override
            public ImmutableSet<Move> getAvailableMoves() {

                HashSet<Move> allMove = new HashSet<>();
                if (remaining.size() == 1 && remaining.contains(mrX.piece())) {
                    allMove.addAll(makeSingleMoves(setup, detectives, mrX, mrX.location()));
                    if (mrX.tickets().getOrDefault(Ticket.DOUBLE, 0) > 0) {
                        allMove.addAll(makeDoubleMoves(setup, detectives, mrX, mrX.location()));
                    }
                }

                for (Player detective : detectives) {
                    if (remaining.contains(detective.piece())) {
                        allMove.addAll(makeSingleMoves(setup, detectives, detective, detective.location()));
                    }

                }

                return ImmutableSet.copyOf(allMove);
            }

        };
        return new MyGameState(setup, startingTurn, ImmutableList.of(), mrX, detectives);
    }

}
