package ch.heigvd.pro.b04.questions;

import ch.heigvd.pro.b04.auth.exceptions.WrongCredentialsException;
import ch.heigvd.pro.b04.error.exceptions.ResourceNotFoundException;
import ch.heigvd.pro.b04.messages.ServerMessage;
import ch.heigvd.pro.b04.moderators.Moderator;
import ch.heigvd.pro.b04.moderators.ModeratorRepository;
import ch.heigvd.pro.b04.participants.Participant;
import ch.heigvd.pro.b04.participants.ParticipantRepository;
import ch.heigvd.pro.b04.polls.ServerPoll;
import ch.heigvd.pro.b04.polls.ServerPollIdentifier;
import ch.heigvd.pro.b04.polls.ServerPollRepository;
import ch.heigvd.pro.b04.polls.exceptions.PollNotExistingException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.transaction.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuestionController {

  private final QuestionRepository repository;
  private final ServerPollRepository pollRepository;
  private final ParticipantRepository participantRepository;
  private final ModeratorRepository moderatorRepository;

  /**
   * Standard constructor.
   *
   * @param repository            repository for {@link ServerQuestion}
   * @param pollRepository        repository for {@link ServerPoll}
   * @param participantRepository repository for {@link Participant}
   * @param moderatorRepository   repository for {@link Moderator}
   */
  public QuestionController(QuestionRepository repository,
      ServerPollRepository pollRepository,
      ParticipantRepository participantRepository,
      ModeratorRepository moderatorRepository) {
    this.repository = repository;
    this.pollRepository = pollRepository;
    this.participantRepository = participantRepository;
    this.moderatorRepository = moderatorRepository;
  }

  /**
   * return all {@link ServerQuestion} of a {@link ServerPoll}.
   *
   * @param idPoll      id of the poll containing question
   * @param token       token of the sender, moderator or participant
   * @param idModerator id of moderator owning the poll
   * @return list of {@link ServerQuestion}
   * @throws ResourceNotFoundException if sender or poll is not found
   * @throws WrongCredentialsException if the sender cannot access this poll
   */
  @GetMapping(value = "/mod/{idModerator}/poll/{idPoll}/question")
  @Transactional
  public List<ServerQuestion> all(
      @RequestParam(name = "token") String token,
      @PathVariable(name = "idModerator") int idModerator,
      @PathVariable(name = "idPoll") int idPoll)
      throws ResourceNotFoundException, WrongCredentialsException {

    // Start authorization code.
    Optional<Boolean> authorizeModerator = moderatorRepository.findByToken(token)
        .filter(moderator -> moderator.getIdModerator() == idModerator)
        .map(moderator -> true);

    Optional<Boolean> authorizeParticipant = participantRepository.findByToken(token)
        .map(participant -> participant.getIdParticipant().getIdxServerSession())
        .map(serverSession -> serverSession.getIdSession().getIdxPoll())
        .filter(poll -> poll.getIdPoll().getIdPoll() == idPoll)
        .filter(poll -> poll.getIdPoll().getIdxModerator().getIdModerator() == idModerator)
        .map(poll -> true);

    boolean authorize = authorizeModerator.isPresent() || authorizeParticipant.isPresent();

    if (!authorize) {
      throw new WrongCredentialsException();
    }

    // We are now guaranteed to have the right to access the data !
    Moderator moderator = moderatorRepository.findById(idModerator)
        .orElseThrow(ResourceNotFoundException::new);

    ServerPoll poll = pollRepository
        .findById(ServerPollIdentifier.builder().idPoll(idPoll).idxModerator(moderator).build())
        .orElseThrow(ResourceNotFoundException::new);

    return repository.findAll()
        .stream()
        .filter(question -> question.getIdServerQuestion().getIdxPoll().equals(poll))
        .collect(Collectors.toList());
  }

  /**
   * Insert a new {@link ServerQuestion} in a {@link ServerPoll}.
   *
   * @param token       sender's token
   * @param question    question to add
   * @param idModerator moderator who should be the sender of the request
   * @param idPoll      poll to add question in
   * @return question added
   * @throws ResourceNotFoundException if one parameter is broken
   * @throws WrongCredentialsException if there is a credentials problem
   */
  @PostMapping(value = "/mod/{idModerator}/poll/{idPoll}/question")
  @Transactional
  public ServerQuestion insert(
      @RequestParam(name = "token") String token,
      @PathVariable(name = "idModerator") int idModerator,
      @PathVariable(name = "idPoll") int idPoll,
      @RequestBody ClientQuestion question
  ) throws ResourceNotFoundException, WrongCredentialsException {

    // Retrieve the associated moderator.
    Moderator moderator = moderatorRepository.findByToken(token)
        .filter(m -> m.getIdModerator() == idModerator)
        .orElseThrow(WrongCredentialsException::new);

    // Retrieve the associated poll.
    ServerPoll poll = pollRepository.findById(ServerPollIdentifier.builder()
        .idxModerator(moderator)
        .idPoll(idPoll)
        .build())
        .orElseThrow(ResourceNotFoundException::new);

    return poll.newQuestion(repository, question);
  }
}
