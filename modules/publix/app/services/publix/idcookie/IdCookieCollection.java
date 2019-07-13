package services.publix.idcookie;

import services.publix.PublixErrorMessages;
import services.publix.idcookie.exception.IdCookieAlreadyExistsException;
import services.publix.idcookie.exception.IdCookieCollectionFullException;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wrapper around a collection of IdCookies. Adds some useful methods. The
 * number of IdCookies are limited to to {@value #MAX_ID_COOKIES}.
 *
 * @author Kristian Lange (2016)
 */
public class IdCookieCollection {

    /**
     * Max number of ID cookies. If all cookies are used and a new study is
     * started, the oldest ID cookie will be overwritten.
     * <p>
     * Important note: jatos.js supports only up to 10 cookies so far.
     */
    public static final int MAX_ID_COOKIES = 10;

    /**
     * Internally we use a map to store the cookies: Each IdCookie has a unique
     * study result ID. We map the study result ID to the actually cookie.
     */
    public final HashMap<Long, IdCookieModel> idCookieMap = new HashMap<>();

    public boolean isFull() {
        return size() >= MAX_ID_COOKIES;
    }

    public int size() {
        return idCookieMap.size();
    }

    /**
     * Stores the given IdCookie. If an IdCookie with the same study result ID
     * is already stored an IdCookieAlreadyExistsException is thrown. If the max
     * number of cookies is reached an IdCookieCollectionFullException is
     * thrown.
     */
    public IdCookieModel add(IdCookieModel idCookie)
            throws IdCookieAlreadyExistsException {
        if (idCookieMap.containsKey(idCookie.getStudyResultId())) {
            throw new IdCookieAlreadyExistsException(PublixErrorMessages
                    .idCookieExistsAlready(idCookie.getStudyResultId()));
        }
        return idCookieMap.put(idCookie.getStudyResultId(), idCookie);
    }

    /**
     * Stores the given IdCookie. If an IdCookie with the same study result ID
     * is already stored it gets overwritten. If the max number of cookies is
     * reached an IdCookieCollectionFullException is thrown.
     */
    public IdCookieModel put(IdCookieModel idCookie)
            throws IdCookieCollectionFullException {
        if (isFull() && !idCookieMap.containsKey(idCookie.getStudyResultId())) {
            throw new IdCookieCollectionFullException(
                    PublixErrorMessages.IDCOOKIE_COLLECTION_FULL);
        }
        return idCookieMap.put(idCookie.getStudyResultId(), idCookie);
    }

    public IdCookieModel remove(IdCookieModel idCookie) {
        return idCookieMap.remove(idCookie.getStudyResultId());
    }

    public Collection<IdCookieModel> getAll() {
        return idCookieMap.values();
    }

    /**
     * Returns a number from 0 to {@value #MAX_ID_COOKIES}. It iterates through
     * the IdCookies and returns the first index that isn't used. Index refers
     * here to the index of the IdCookie which is the last char of its name. If
     * this IdCookieCollection is full a IndexOutOfBoundsException will be
     * thrown.
     */
    public int getNextAvailableIdCookieIndex() {
        if (isFull()) {
            throw new IndexOutOfBoundsException(
                    PublixErrorMessages.IDCOOKIE_COLLECTION_INDEX_OUT_OF_BOUND);
        }
        List<Integer> existingIndices = idCookieMap.values().stream()
                .map(IdCookieModel::getIndex).collect(Collectors.toList());
        for (int i = 0; i < MAX_ID_COOKIES; i++) {
            if (!existingIndices.contains(i)) {
                return i;
            }
        }
        throw new IndexOutOfBoundsException(
                PublixErrorMessages.IDCOOKIE_COLLECTION_INDEX_OUT_OF_BOUND);
    }

    /**
     * Returns the IdCookie to which the specified study result ID is mapped, or
     * null if nothing maps to the ID.
     */
    public IdCookieModel get(long studyResultId) {
        return idCookieMap.get(studyResultId);
    }

    public boolean contains(long studyResultId) {
        return idCookieMap.containsKey(studyResultId);
    }

    @Override
    public String toString() {
        return idCookieMap.keySet().stream().map(Object::toString)
                .collect(Collectors.joining(", "));
    }

}
