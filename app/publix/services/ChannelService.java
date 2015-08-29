package publix.services;

import static akka.pattern.Patterns.ask;
import models.GroupResult;
import models.StudyResult;
import play.libs.Akka;
import play.mvc.Controller;
import play.mvc.WebSocket;
import publix.akka.actors.GroupDispatcherRegistry;
import publix.akka.messages.Get;
import publix.akka.messages.GetOrCreate;
import publix.akka.messages.IsMember;
import publix.akka.messages.ItsThisOne;
import publix.akka.messages.PoisonSomeone;
import publix.exceptions.ForbiddenPublixException;
import publix.exceptions.InternalServerErrorPublixException;
import publix.exceptions.NotFoundPublixException;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import akka.actor.ActorRef;
import akka.util.Timeout;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Singleton;

@Singleton
public class ChannelService {

	private static final Timeout TIMEOUT = new Timeout(Duration.create(1000,
			"seconds"));
	private static final ActorRef GROUP_DISPATCHER_REGISTRY = Akka.system()
			.actorOf(GroupDispatcherRegistry.props());

	public WebSocket<JsonNode> openGroupChannel(StudyResult studyResult,
			GroupResult groupResult) throws InternalServerErrorPublixException,
			ForbiddenPublixException, NotFoundPublixException {
		// Get the GroupDispatcher that will handle this GroupResult. Create a
		// new one or get the already existing one.
		ActorRef groupDispatcher = retrieveGroupDispatcher(new GetOrCreate(
				groupResult.getId()));
		if (isMemberOfGroup(studyResult, groupDispatcher)) {
			// This studyResult is already member of a group
			return WebSocketBuilder.reject(Controller.badRequest());
		}
		return WebSocketBuilder.withGroupChannelActor(studyResult.getId(),
				groupDispatcher);
	}

	public void closeGroupChannel(StudyResult studyResult)
			throws InternalServerErrorPublixException {
		GroupResult groupResult = studyResult.getGroupResult();
		if (groupResult == null) {
			return;
		}
		ActorRef groupDispatcher = retrieveGroupDispatcher(new Get(
				groupResult.getId()));
		if (groupDispatcher != null) {
			groupDispatcher.tell(new PoisonSomeone(studyResult.getId()),
					ActorRef.noSender());
		}
	}

	private ActorRef retrieveGroupDispatcher(Object msg)
			throws InternalServerErrorPublixException {
		Future<Object> future = ask(GROUP_DISPATCHER_REGISTRY, msg, TIMEOUT);
		Object answer;
		try {
			answer = Await.result(future, TIMEOUT.duration());
		} catch (Exception e) {
			throw new InternalServerErrorPublixException(e.getMessage());
		}
		return ((ItsThisOne) answer).channel;
	}

	private boolean isMemberOfGroup(StudyResult studyResult,
			ActorRef groupDispatcher) throws InternalServerErrorPublixException {
		Future<Object> future = ask(groupDispatcher,
				new IsMember(studyResult.getId()), TIMEOUT);
		boolean result;
		try {
			result = (boolean) Await.result(future, TIMEOUT.duration());
		} catch (Exception e) {
			throw new InternalServerErrorPublixException(e.getMessage());
		}
		return result;
	}

}