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
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class NetrunnersUnitsHandler {
    public static final String MECH_ID = "netrunners_mech";

    public static void offerNimdaDeploy(Game game, Player player) {
        if (game == null
                || player == null
                || "setup".equalsIgnoreCase(game.getPhaseOfGame())
                || !player.hasUnit(MECH_ID)
                || ButtonHelper.isLawInPlay(game, "articles_war")
                || ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) >= 4) return;
        List<Button> buttons = new ArrayList<>(player.getPlanets().stream()
                .filter(planet -> game.getTileFromPlanet(planet) != null)
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + "netrunnersNimda_" + planet,
                        "Deploy Nimda to " + Helper.getPlanetRepresentation(planet, game)))
                .toList());
        if (buttons.isEmpty()) return;
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", you gained a technology. You may place Nimda, the Netrunners mech, from your reinforcements on a planet you control.",
                buttons);
    }

    @ButtonHandler("netrunnersNimda_")
    public static void resolveNimdaDeploy(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.replace("netrunnersNimda_", "");
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null
                || !player.getPlanets().contains(planet)
                || "setup".equalsIgnoreCase(game.getPhaseOfGame())
                || !player.hasUnit(MECH_ID)
                || ButtonHelper.isLawInPlay(game, "articles_war")
                || ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) >= 4) return;
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech " + planet);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " deployed Nimda to " + Helper.getPlanetRepresentation(planet, game)
                        + ".");
    }
}
