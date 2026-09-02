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
import ti4.service.tech.ListTechService;
import ti4.service.tech.PlayerTechService;

@UtilityClass
public class NetrunnersStartingTechsHandler {
    private static final String STARTING_TECH_COUNT = "netrunnersStartingTechCount";

    public static boolean offerStartingTechs(Game game, Player player, String faction) {
        if (game == null
                || player == null
                || !"netrunners".equalsIgnoreCase(faction == null ? player.getFaction() : faction)) {
            return false;
        }
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + " press this after other players have selected their starting technologies to choose your Netrunners starting technologies.",
                Buttons.green(player.factionButtonChecker() + "netrunnersStartingTechs", "Get Starting Tech Options"));
        return true;
    }

    @ButtonHandler("netrunnersStartingTechs")
    public static void resolveStartingTechs(ButtonInteractionEvent event, Game game, Player player) {
        if (getStartingTechCount(game, player) >= 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getStartingTechButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + " has no technology owned by another player available for their starting technologies.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        sendStartingTechButtons(game, player, player.getCorrectChannel());
    }

    @ButtonHandler("netrunnersStartingTech_")
    public static void resolveStartingTech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getStartingTechButtons(game, player);
        String message = player.getRepresentationUnfogged()
                + ", choose starting technology " + (getStartingTechCount(game, player) + 1)
                + " of 2 that is owned by at least 1 other player.";
        String buttonPrefix = player.factionButtonChecker() + "netrunnersStartingTech_";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) return;
        String techId = buttonID.replace("netrunnersStartingTech_", "");
        if (getStartingTechCount(game, player) >= 2
                || buttons.stream().noneMatch(button -> button.getCustomId().endsWith("_" + techId))) {
            return;
        }
        game.setStoredValue(
                STARTING_TECH_COUNT + player.getFaction(), Integer.toString(getStartingTechCount(game, player) + 1));
        ButtonHelper.deleteMessage(event);
        PlayerTechService.addTech(event, game, player, techId);
        if (getStartingTechCount(game, player) < 2) {
            sendStartingTechButtons(game, player, event.getMessageChannel());
        }
    }

    private static List<Button> getStartingTechButtons(Game game, Player player) {
        List<ti4.model.TechnologyModel> techs = game.getTechnologyDeck().stream()
                .map(Mapper::getTech)
                .filter(java.util.Objects::nonNull)
                .filter(tech -> tech.getFaction().isEmpty())
                .filter(tech -> !player.hasTech(tech.getAlias()))
                .filter(tech -> game.getRealPlayersExcludingThis(player).stream()
                        .anyMatch(other -> other.hasTech(tech.getAlias())))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<Button> buttons = ListTechService.getTechButtons(techs, player);
        for (int index = 0; index < buttons.size(); index++) {
            buttons.set(
                    index,
                    buttons.get(index)
                            .withCustomId(player.factionButtonChecker() + "netrunnersStartingTech_"
                                    + techs.get(index).getAlias()));
        }
        return buttons;
    }

    private static int getStartingTechCount(Game game, Player player) {
        try {
            return Integer.parseInt(game.getStoredValue(STARTING_TECH_COUNT + player.getFaction()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static void sendStartingTechButtons(
            Game game, Player player, net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel) {
        List<Button> buttons = getStartingTechButtons(game, player);
        if (buttons.isEmpty()) return;
        String message = player.getRepresentationUnfogged()
                + ", choose starting technology " + (getStartingTechCount(game, player) + 1)
                + " of 2 that is owned by at least 1 other player.";
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                message,
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + "netrunnersStartingTech_", 0));
    }
}
