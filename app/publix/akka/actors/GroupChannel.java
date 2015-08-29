package publix.akka.actors;

import publix.akka.messages.Droppout;
import publix.akka.messages.GroupMsg;
import publix.akka.messages.JoinGroup;
import publix.akka.messages.PoisonSomeone;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * GroupChannelActor is an Akka Actor that is used by the group channel. A group
 * channel is a WebSocket connecting a client who's running a study and the
 * JATOS server. A GroupChannelActor belongs to a group, which is managed be a
 * GroupActor. A GroupChannelActor joins it's group by sending the JoinMessage
 * to it's GroupActor.
 * 
 * @author Kristian Lange
 */
public class GroupChannel extends UntypedActor {

	/**
	 * Output channel of the WebSocket: JATOS -> client
	 */
	private final ActorRef out;
	private final long studyResultId;
	private final ActorRef groupDispatcher;

	public static Props props(ActorRef out, long studyResultId,
			ActorRef groupDispatcher) {
		return Props.create(GroupChannel.class, out, studyResultId,
				groupDispatcher);
	}

	public GroupChannel(ActorRef out, long studyResultId,
			ActorRef groupDispatcher) {
		this.out = out;
		this.studyResultId = studyResultId;
		this.groupDispatcher = groupDispatcher;
	}

	@Override
	public void preStart() {
		groupDispatcher.tell(new JoinGroup(studyResultId), self());
	}

	@Override
	public void postStop() {
		groupDispatcher.tell(new Droppout(studyResultId), self());
	}

	@Override
	// WebSocket's input channel: client -> JATOS
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof ObjectNode) {
			// If we receive a JsonNode (only from the client) wrap it in a
			// GroupMsg and forward it to the GroupDispatcher
			ObjectNode jsonNode = (ObjectNode) msg;
			groupDispatcher.tell(new GroupMsg(jsonNode), self());
		} else if (msg instanceof GroupMsg) {
			// If we receive a GroupMessage (only from the GroupDispatcher) send
			// the wrapped JsonNode to the client
			GroupMsg groupMsg = (GroupMsg) msg;
			out.tell(groupMsg.jsonNode, self());
		} else if (msg instanceof PoisonSomeone) {
			// Kill this group channel
			self().tell(PoisonPill.getInstance(), self());
		}
	}

}