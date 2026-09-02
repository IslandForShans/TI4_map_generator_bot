package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.tech.ListTechService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class NetrunnersBreakthroughHandler {
    private static final String EMOTET = "netrunnersbt";

    public static void startEmotet(Game game, Player player, GenericInteractionCreateEvent event) {
        if (game == null || player == null || !player.hasBreakthrough(EMOTET)) return;
        List<Button> buttons = game.getRealPlayersExcludingThis(player).stream()
                .filter(target -> target.getTg() >= 2)
                .map(target -> Buttons.green(
                        player.factionButtonChecker() + "netrunnersEmotetTarget_" + target.getFaction(),
                        "Choose " + target.getColorDisplayName()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged() + " has no other player with 2 trade goods for _Emotet_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the player who will spend 2 trade goods to research a non-faction technology via _Emotet_.",
                buttons);
    }

    @ButtonHandler("netrunnersEmotetTarget_")
    public static void chooseEmotetTechnology(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("netrunnersEmotetTarget_", ""));
        if (target == null || target == player || target.getTg() < 2 || !player.hasBreakthrough(EMOTET)) return;
        List<ti4.model.TechnologyModel> techs = game.getTechnologyDeck().stream()
                .map(Mapper::getTech)
                .filter(java.util.Objects::nonNull)
                .filter(tech -> tech.getFaction().isEmpty() && !tech.isUnitUpgrade())
                .filter(tech -> !target.hasTech(tech.getAlias()))
                .filter(tech -> ListTechService.isTechResearchable(tech, target))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<Button> buttons = ListTechService.getTechButtons(techs, target);
        for (int index = 0; index < buttons.size(); index++) {
            buttons.set(
                    index,
                    buttons.get(index)
                            .withCustomId(target.factionButtonChecker() + "netrunnersEmotetTech_" + player.getFaction()
                                    + "|" + target.getFaction() + "|"
                                    + techs.get(index).getAlias()));
        }
        if (buttons.isEmpty()) return;
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged()
                        + ", choose the non-faction technology to research for 2 trade goods via _Emotet_.",
                NewStuffHelper.buttonPagination(
                        buttons,
                        target.factionButtonChecker() + "netrunnersEmotetTech_" + player.getFaction() + "|"
                                + target.getFaction() + "|",
                        0));
    }

    @ButtonHandler("netrunnersEmotetTech_")
    public static void resolveEmotetTechnology(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("netrunnersEmotetTech_", "").split("\\|", 3);
        Player owner = parts.length == 3 ? game.getPlayerFromColorOrFaction(parts[0]) : null;
        Player target = parts.length == 3 ? game.getPlayerFromColorOrFaction(parts[1]) : null;
        if (owner != null && target == player && target != owner && owner.hasBreakthrough(EMOTET)) {
            List<ti4.model.TechnologyModel> techs = game.getTechnologyDeck().stream()
                    .map(Mapper::getTech)
                    .filter(java.util.Objects::nonNull)
                    .filter(candidate -> candidate.getFaction().isEmpty() && !candidate.isUnitUpgrade())
                    .filter(candidate -> !target.hasTech(candidate.getAlias()))
                    .filter(candidate -> ListTechService.isTechResearchable(candidate, target))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            List<Button> buttons = ListTechService.getTechButtons(techs, target);
            for (int index = 0; index < buttons.size(); index++) {
                buttons.set(
                        index,
                        buttons.get(index)
                                .withCustomId(target.factionButtonChecker() + "netrunnersEmotetTech_"
                                        + owner.getFaction() + "|" + target.getFaction() + "|"
                                        + techs.get(index).getAlias()));
            }
            String message = target.getRepresentationUnfogged()
                    + ", choose the non-faction technology to research for 2 trade goods via _Emotet_.";
            String buttonPrefix = target.factionButtonChecker() + "netrunnersEmotetTech_" + owner.getFaction() + "|"
                    + target.getFaction() + "|";
            if (NewStuffHelper.checkAndHandlePaginationChange(
                    event, target.getCorrectChannel(), buttons, message, buttonPrefix, buttonID)) {
                return;
            }
        }
        var tech = parts.length == 3 ? Mapper.getTech(parts[2]) : null;
        if (owner == null
                || target == null
                || target != player
                || target == owner
                || tech == null
                || !owner.hasBreakthrough(EMOTET)
                || target.getTg() < 2
                || tech.getFaction().isPresent()
                || tech.isUnitUpgrade()
                || target.hasTech(tech.getAlias())
                || !ListTechService.isTechResearchable(tech, target)) return;
        target.setTg(target.getTg() - 2);
        target.addTech(tech.getAlias());
        NetrunnersAbilitiesHandler.offerNeuralInstruments(game, target);
        NetrunnersFactionTechsHandler.resolveDataMining(game, target, tech.getAlias());
        NetrunnersLeadersHandler.offerAgentTechnologyReplacement(game, target, tech.getAlias());
        ti4.service.leader.CommanderUnlockCheckService.checkAllPlayersInGame(game, "netrunners");
        NetrunnersUnitsHandler.offerNimdaDeploy(game, target);
        if (owner.hasReadyBreakthrough(EMOTET)) {
            BreakthroughCommandHelper.exhaustBreakthrough(owner, EMOTET);
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                target.getRepresentationNoPing() + " spent 2 trade goods and researched " + tech.getNameRepresentation()
                        + " via _Emotet_.");
        if (owner.getDebtTokenCount(target.getColor(), NetrunnersAbilitiesHandler.CONTROL_TOKEN_POOL) < 1) return;
        List<Button> planets = target.getPlanets().stream()
                .filter(planet -> game.getTileFromPlanet(planet) != null)
                .map(planet -> Buttons.green(
                        owner.factionButtonChecker() + "netrunnersEmotetCoexist_" + target.getFaction() + "|"
                                + tech.getAlias() + "|" + planet,
                        "Coexist on " + Helper.getPlanetRepresentation(planet, game)))
                .toList();
        if (!planets.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    owner.getCorrectChannel(),
                    owner.getRepresentationUnfogged() + ", you may return "
                            + target.getRepresentation(false, true) + "'s control token to gain "
                            + tech.getNameRepresentation() + " and place 1 infantry into coexistence.",
                    planets);
        }
    }

    @ButtonHandler("netrunnersEmotetCoexist_")
    public static void resolveEmotetCoexistence(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("netrunnersEmotetCoexist_", "").split("\\|", 3);
        Player target = parts.length == 3 ? game.getPlayerFromColorOrFaction(parts[0]) : null;
        Tile tile = parts.length == 3 ? game.getTileFromPlanet(parts[2]) : null;
        if (target == null
                || tile == null
                || !target.getPlanets().contains(parts[2])
                || player.getDebtTokenCount(target.getColor(), NetrunnersAbilitiesHandler.CONTROL_TOKEN_POOL) < 1
                || Mapper.getTech(parts[1]) == null
                || player.hasTech(parts[1])) return;
        player.clearDebt(target, 1, NetrunnersAbilitiesHandler.CONTROL_TOKEN_POOL);
        player.addTech(parts[1]);
        NetrunnersAbilitiesHandler.offerNeuralInstruments(game, player);
        NetrunnersFactionTechsHandler.resolveDataMining(game, player, parts[1]);
        NetrunnersLeadersHandler.offerAgentTechnologyReplacement(game, player, parts[1]);
        ti4.service.leader.CommanderUnlockCheckService.checkAllPlayersInGame(game, "netrunners");
        NetrunnersUnitsHandler.offerNimdaDeploy(game, player);
        game.setStoredValue("coexistFlag", "yes");
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 inf " + parts[2]);
        game.removeStoredValue("coexistFlag");
        ButtonHelperAbilities.oceanBoundCheck(game);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " returned " + target.getRepresentation(false, true) + "'s control token, gained "
                        + Mapper.getTech(parts[1]).getNameRepresentation()
                        + ", and placed 1 infantry into coexistence on "
                        + Helper.getPlanetRepresentation(parts[2], game) + " via _Emotet_.");
    }
}
