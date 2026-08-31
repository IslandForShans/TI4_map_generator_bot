package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;

@UtilityClass
public class NetrunnersPromissoryHandler {
    public static final String SHARED_NETWORK_ACCESS = "bepnnetrunners";

    public static boolean hasSharedNetworkAccessInHand(Player player) {
        return player != null && player.hasPlayablePromissoryInHand(SHARED_NETWORK_ACCESS);
    }

    public static Button getSharedNetworkAccessPaymentButton(Player player, TechnologyModel technology) {
        if (technology == null
                || technology.isUnitUpgrade()
                || technology.getFaction().isPresent()
                || !hasSharedNetworkAccessInHand(player)) return null;
        return Buttons.gray(player.factionButtonChecker() + "netrunnersSharedNetworkPay", "Use Shared Network Access");
    }

    @ButtonHandler("netrunnersSharedNetworkPay")
    public static void resolveSharedNetworkAccess(Game game, Player player, ButtonInteractionEvent event) {
        if (!hasSharedNetworkAccessInHand(player)) return;
        PromissoryNoteHelper.resolvePNPlay(SHARED_NETWORK_ACCESS, player, game, event);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
    }

    public static void returnSharedNetworkAccessAtStartOfStatus(Game game) {
        if (game == null) return;
        for (Player holder : game.getRealPlayers()) {
            if (!holder.getPromissoryNotesInPlayArea().contains(SHARED_NETWORK_ACCESS)) continue;
            Player owner = game.getPNOwner(SHARED_NETWORK_ACCESS);
            if (owner == null || owner == holder) continue;
            holder.removePromissoryNote(SHARED_NETWORK_ACCESS);
            owner.setPromissoryNote(SHARED_NETWORK_ACCESS);
            PromissoryNoteHelper.sendPromissoryNoteInfo(game, holder, false);
            PromissoryNoteHelper.sendPromissoryNoteInfo(game, owner, false);
            MessageHelper.sendMessageToChannel(
                    holder.getCorrectChannel(),
                    "_Shared Network Access_ has been returned to " + owner.getRepresentationNoPing() + ".");
        }
    }
}
