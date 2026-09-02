package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.tech.ListTechService;

@UtilityClass
public class NetrunnersLeadersHandler {
    public static boolean commanderSkipsTechnologySecondaryToken(Game game, Player player) {
        return game != null && player != null && game.playerHasLeaderUnlockedOrAlliance(player, "netrunnerscommander");
    }

    public static Button getAgentCardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + "netrunnersAgentOther",
                "Use Netrunners Agent on Another Player",
                ti4.service.emoji.FactionEmojis.netrunners);
    }

    @ButtonHandler("netrunnersAgentOther")
    public static void chooseAgentTarget(Game game, Player player, ButtonInteractionEvent event) {
        if ("setup".equalsIgnoreCase(game.getPhaseOfGame()) || !player.hasUnexhaustedLeader("netrunnersagent")) return;
        List<Button> buttons = game.getRealPlayersExcludingThis(player).stream()
                .map(target -> Buttons.gray(
                        player.factionButtonChecker() + "netrunnersAgentTarget_" + target.getFaction(),
                        "Use on " + target.getColorDisplayName()))
                .toList();
        if (buttons.isEmpty()) return;
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", choose the player using Zor No-ahn, the Netrunners agent.",
                buttons);
    }

    @ButtonHandler("netrunnersAgentTarget_")
    public static void chooseAgentTechnology(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        if ("setup".equalsIgnoreCase(game.getPhaseOfGame()) || !player.hasUnexhaustedLeader("netrunnersagent")) return;
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("netrunnersAgentTarget_", ""));
        if (target == null || target == player) return;
        List<TechnologyModel> techs = target.getTechs().stream()
                .map(Mapper::getTech)
                .filter(java.util.Objects::nonNull)
                .filter(tech -> tech.getFaction().isEmpty())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<Button> buttons = ListTechService.getTechButtons(techs, player);
        for (int index = 0; index < buttons.size(); index++) {
            buttons.set(
                    index,
                    buttons.get(index)
                            .withCustomId(
                                    player.factionButtonChecker() + "netrunnersAgentReplace_" + target.getFaction()
                                            + "|" + techs.get(index).getAlias()));
        }
        if (buttons.isEmpty()) return;
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", choose the technology to replace for "
                        + target.getRepresentation(false, true) + ".",
                NewStuffHelper.buttonPagination(
                        buttons,
                        player.factionButtonChecker() + "netrunnersAgentReplace_" + target.getFaction() + "|",
                        0));
    }

    public static void offerAgentTechnologyReplacement(Game game, Player techGainer, String techId) {
        if (game == null
                || techGainer == null
                || "setup".equalsIgnoreCase(game.getPhaseOfGame())
                || Mapper.getTech(techId) == null
                || !techGainer.hasUnexhaustedLeader("netrunnersagent")) return;
        MessageHelper.sendMessageToChannelWithButtons(
                techGainer.getCorrectChannel(),
                techGainer.getRepresentationUnfogged() + ", you gained "
                        + Mapper.getTech(techId).getNameRepresentation()
                        + ". You may exhaust Zor No-ahn, the Netrunners agent to replace it.",
                List.of(
                        Buttons.green(
                                techGainer.factionButtonChecker() + "netrunnersAgentReplace_" + techGainer.getFaction()
                                        + "|" + techId,
                                "Use Zor No-ahn, the Netrunners Agent"),
                        Buttons.red(techGainer.factionButtonChecker() + "deleteButtons", "Decline")));
    }

    @ButtonHandler("netrunnersAgentReplace_")
    public static void resolveAgentTechnologyReplacement(
            Game game, Player netrunner, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("netrunnersAgentReplace_", "").split("\\|", 2);
        if (parts.length != 2
                || "setup".equalsIgnoreCase(game.getPhaseOfGame())
                || !netrunner.hasUnexhaustedLeader("netrunnersagent")) return;
        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        if (target != null && parts[1].matches("page\\d+")) {
            List<TechnologyModel> techs = target.getTechs().stream()
                    .map(Mapper::getTech)
                    .filter(java.util.Objects::nonNull)
                    .filter(tech -> tech.getFaction().isEmpty())
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            List<Button> buttons = ListTechService.getTechButtons(techs, netrunner);
            for (int index = 0; index < buttons.size(); index++) {
                buttons.set(
                        index,
                        buttons.get(index)
                                .withCustomId(netrunner.factionButtonChecker() + "netrunnersAgentReplace_"
                                        + target.getFaction() + "|"
                                        + techs.get(index).getAlias()));
            }
            String message = netrunner.getRepresentationUnfogged() + ", choose the technology to replace for "
                    + target.getRepresentation(false, true) + ".";
            String buttonPrefix =
                    netrunner.factionButtonChecker() + "netrunnersAgentReplace_" + target.getFaction() + "|";
            if (NewStuffHelper.checkAndHandlePaginationChange(
                    event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
                return;
            }
        }
        TechnologyModel returnedTech = Mapper.getTech(parts[1]);
        if (target == null
                || returnedTech == null
                || returnedTech.getFaction().isPresent()
                || !target.hasTech(parts[1])) return;
        List<TechnologyModel> techs = game.getTechnologyDeck().stream()
                .map(Mapper::getTech)
                .filter(java.util.Objects::nonNull)
                .filter(tech -> tech.getFaction().isEmpty())
                .filter(tech -> !target.hasTech(tech.getAlias()) && tech.getFirstType() != returnedTech.getFirstType())
                .filter(tech -> tech.getRequirements().orElse("").length()
                        == returnedTech.getRequirements().orElse("").length())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<Button> buttons = ListTechService.getTechButtons(techs, target);
        for (int index = 0; index < buttons.size(); index++) {
            buttons.set(
                    index,
                    buttons.get(index)
                            .withCustomId(target.factionButtonChecker() + "netrunnersAgentReplacementTech_"
                                    + netrunner.getFaction() + "|" + target.getFaction() + "|" + parts[1] + "|"
                                    + techs.get(index).getAlias()));
        }
        if (buttons.isEmpty()) return;
        ExhaustLeaderService.exhaustLeader(
                game, netrunner, netrunner.getLeader("netrunnersagent").orElseThrow());
        target.removeTech(parts[1]);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged()
                        + ", choose a technology in a different color with the same number of prerequisites to gain via Zor No-ahn, the Netrunners agent.",
                NewStuffHelper.buttonPagination(
                        buttons,
                        target.factionButtonChecker() + "netrunnersAgentReplacementTech_" + netrunner.getFaction() + "|"
                                + target.getFaction() + "|" + parts[1] + "|",
                        0));
    }

    @ButtonHandler("netrunnersAgentReplacementTech_")
    public static void resolveAgentReplacementTechnology(
            Game game, Player target, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("netrunnersAgentReplacementTech_", "").split("\\|", 4);
        if (parts.length != 4) return;
        Player netrunner = game.getPlayerFromColorOrFaction(parts[0]);
        if (netrunner == null
                || target != game.getPlayerFromColorOrFaction(parts[1])
                || !netrunner.hasLeader("netrunnersagent")) {
            return;
        }
        TechnologyModel returnedTech = Mapper.getTech(parts[2]);
        if (returnedTech != null && parts[3].matches("page\\d+")) {
            List<TechnologyModel> techs = game.getTechnologyDeck().stream()
                    .map(Mapper::getTech)
                    .filter(java.util.Objects::nonNull)
                    .filter(tech -> tech.getFaction().isEmpty())
                    .filter(tech ->
                            !target.hasTech(tech.getAlias()) && tech.getFirstType() != returnedTech.getFirstType())
                    .filter(tech -> tech.getRequirements().orElse("").length()
                            == returnedTech.getRequirements().orElse("").length())
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            List<Button> buttons = ListTechService.getTechButtons(techs, target);
            for (int index = 0; index < buttons.size(); index++) {
                buttons.set(
                        index,
                        buttons.get(index)
                                .withCustomId(target.factionButtonChecker() + "netrunnersAgentReplacementTech_"
                                        + netrunner.getFaction() + "|" + target.getFaction() + "|" + parts[2] + "|"
                                        + techs.get(index).getAlias()));
            }
            String message = target.getRepresentationUnfogged()
                    + ", choose a technology in a different color with the same number of prerequisites to gain via Zor No-ahn, the Netrunners agent.";
            String buttonPrefix = target.factionButtonChecker() + "netrunnersAgentReplacementTech_"
                    + netrunner.getFaction() + "|" + target.getFaction() + "|" + parts[2] + "|";
            if (NewStuffHelper.checkAndHandlePaginationChange(
                    event, target.getCorrectChannel(), buttons, message, buttonPrefix, buttonID)) {
                return;
            }
        }
        TechnologyModel replacementTech = Mapper.getTech(parts[3]);
        if (returnedTech == null
                || replacementTech == null
                || replacementTech.getFaction().isPresent()
                || target.hasTech(parts[2])
                || target.hasTech(parts[3])
                || replacementTech.getFirstType() == returnedTech.getFirstType()
                || replacementTech.getRequirements().orElse("").length()
                        != returnedTech.getRequirements().orElse("").length()) return;
        ButtonHelper.deleteMessage(event);
        target.addTech(parts[3]);
        NetrunnersAbilitiesHandler.offerNeuralInstruments(game, target);
        NetrunnersFactionTechsHandler.resolveDataMining(game, target, parts[3]);
        CommanderUnlockCheckService.checkAllPlayersInGame(game, "netrunners");
        NetrunnersUnitsHandler.offerNimdaDeploy(game, target);
        MessageHelper.sendMessageToChannel(
                target.getCorrectChannel(),
                target.getRepresentationNoPing() + " returned "
                        + returnedTech.getNameRepresentation() + " and gained "
                        + replacementTech.getNameRepresentation()
                        + " via Zor No-ahn, the Netrunners agent.");
    }

    public static void offerHeroTechSelection(Game game, Player player) {
        if (game == null || player == null) return;
        List<TechnologyModel> techs = player.getTechs().stream()
                .map(Mapper::getTech)
                .filter(java.util.Objects::nonNull)
                .filter(tech -> !tech.isUnitUpgrade())
                .filter(tech -> game.getRealPlayersExcludingThis(player).stream()
                        .anyMatch(other -> other.hasTech(tech.getAlias())))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<Button> buttons = ListTechService.getTechButtons(techs, player);
        for (int index = 0; index < buttons.size(); index++) {
            buttons.set(
                    index,
                    buttons.get(index)
                            .withCustomId(player.factionButtonChecker() + "netrunnersHeroTech_"
                                    + techs.get(index).getAlias()));
        }
        if (buttons.isEmpty()) return;
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the shared technology to return with _Power Surge - Network Overload_.",
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + "netrunnersHeroTech_", 0));
    }

    @ButtonHandler("netrunnersHeroTech_")
    public static void resolveHeroTech(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<TechnologyModel> techs = player.getTechs().stream()
                .map(Mapper::getTech)
                .filter(java.util.Objects::nonNull)
                .filter(tech -> !tech.isUnitUpgrade())
                .filter(tech -> game.getRealPlayersExcludingThis(player).stream()
                        .anyMatch(other -> other.hasTech(tech.getAlias())))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<Button> buttons = ListTechService.getTechButtons(techs, player);
        for (int index = 0; index < buttons.size(); index++) {
            buttons.set(
                    index,
                    buttons.get(index)
                            .withCustomId(player.factionButtonChecker() + "netrunnersHeroTech_"
                                    + techs.get(index).getAlias()));
        }
        String message = player.getRepresentationUnfogged()
                + ", choose the shared technology to return with _Power Surge - Network Overload_.";
        String buttonPrefix = player.factionButtonChecker() + "netrunnersHeroTech_";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, player.getCorrectChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }
        String techId = buttonID.replace("netrunnersHeroTech_", "");
        TechnologyModel tech = Mapper.getTech(techId);
        if (tech == null
                || tech.isUnitUpgrade()
                || !player.hasTech(techId)
                || game.getRealPlayersExcludingThis(player).stream().noneMatch(other -> other.hasTech(techId))) return;
        for (Player owner : game.getRealPlayersExcludingThis(player))
            if (owner.hasTech(techId)) owner.removeTech(techId);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " returned " + tech.getNameRepresentation()
                        + " for every other owner via _Power Surge - Network Overload_.");
    }
}
