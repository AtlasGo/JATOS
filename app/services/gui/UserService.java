package services.gui;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import models.UserModel;
import persistance.UserDao;
import play.data.validation.ValidationError;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import common.RequestScope;

import controllers.gui.Authentication;
import exceptions.ForbiddenException;
import exceptions.NotFoundException;

/**
 * Service class mostly for Users controller. Handles everything around
 * UserModel.
 * 
 * @author Kristian Lange
 */
@Singleton
public class UserService {

	public static final String ADMIN_EMAIL = "admin";
	public static final String ADMIN_PASSWORD = "admin";
	public static final String ADMIN_NAME = "Admin";

	private final UserDao userDao;

	@Inject
	UserService(UserDao userDao) {
		this.userDao = userDao;
	}

	/**
	 * Retrieves the user with the given email form the DB. Throws an Exception
	 * if it doesn't exist.
	 */
	public UserModel retrieveUser(String email) throws NotFoundException {
		UserModel user = userDao.findByEmail(email);
		if (user == null) {
			throw new NotFoundException(MessagesStrings.userNotExist(email));
		}
		return user;
	}

	/**
	 * Retrieves the user with the given email form the RequestScope. It was put
	 * into the RequestScope by the AuthenticationAction.
	 */
	public UserModel retrieveLoggedInUser() {
		return (UserModel) RequestScope.get(Authentication.LOGGED_IN_USER);
	}

	/**
	 * Throws an Exception in case the user isn't equal to the
	 * loggedInUser.
	 */
	public void checkUserLoggedIn(UserModel user, UserModel loggedInUser)
			throws ForbiddenException {
		if (!user.equals(loggedInUser)) {
			throw new ForbiddenException(
					MessagesStrings.mustBeLoggedInAsUser(user));
		}
	}

	public UserModel createAdmin() throws UnsupportedEncodingException,
			NoSuchAlgorithmException {
		String passwordHash = getHashMDFive(ADMIN_PASSWORD);
		UserModel adminUser = new UserModel(ADMIN_EMAIL, ADMIN_NAME,
				passwordHash);
		userDao.create(adminUser);
		return adminUser;
	}

	public String getHashMDFive(String str)
			throws UnsupportedEncodingException, NoSuchAlgorithmException {
		byte[] strBytes = str.getBytes("UTF-8");
		MessageDigest md = MessageDigest.getInstance("MD5");
		byte[] hashByte = md.digest(strBytes);

		// Convert the byte to hex format
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < hashByte.length; i++) {
			sb.append(Integer.toString((hashByte[i] & 0xff) + 0x100, 16)
					.substring(1));
		}
		return sb.toString();
	}

	public List<ValidationError> validateNewUser(UserModel newUser,
			String password, String passwordRepeat)
			throws UnsupportedEncodingException, NoSuchAlgorithmException {
		List<ValidationError> errorList = new ArrayList<ValidationError>();

		// Check if user with this email already exists.
		if (userDao.findByEmail(newUser.getEmail()) != null) {
			errorList.add(new ValidationError(UserModel.EMAIL,
					MessagesStrings.THIS_EMAIL_IS_ALREADY_REGISTERED));
		}

		checkPasswords(password, passwordRepeat, errorList);
		return errorList;
	}

	public List<ValidationError> validateChangePassword(UserModel user,
			String password, String passwordRepeat, String oldPasswordHash)
			throws UnsupportedEncodingException, NoSuchAlgorithmException {
		List<ValidationError> errorList = new ArrayList<ValidationError>();

		if (userDao.authenticate(user.getEmail(), oldPasswordHash) == null) {
			errorList.add(new ValidationError(UserModel.OLD_PASSWORD,
					MessagesStrings.WRONG_OLD_PASSWORD));
		}

		checkPasswords(password, passwordRepeat, errorList);
		return errorList;
	}

	public void checkPasswords(String password, String passwordRepeat,
			List<ValidationError> errorList)
			throws UnsupportedEncodingException, NoSuchAlgorithmException {

		// Check for non empty passwords
		if (password.trim().isEmpty() || passwordRepeat.trim().isEmpty()) {
			errorList.add(new ValidationError(UserModel.PASSWORD,
					MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS));
		}

		// Check that both passwords are the same
		String passwordHash = getHashMDFive(password);
		String passwordHashRepeat = getHashMDFive(passwordRepeat);
		if (!passwordHash.equals(passwordHashRepeat)) {
			errorList.add(new ValidationError(UserModel.PASSWORD,
					MessagesStrings.PASSWORDS_DONT_MATCH));
		}
	}

	/**
	 * Creates a user, sets password hash and persists it.
	 */
	public void createUser(UserModel newUser, String password)
			throws UnsupportedEncodingException, NoSuchAlgorithmException {
		String passwordHash = getHashMDFive(password);
		newUser.setPasswordHash(passwordHash);
		userDao.create(newUser);
	}

	/**
	 * Change password hash and persist user.
	 */
	public void changePasswordHash(UserModel user, String newPasswordHash) {
		user.setPasswordHash(newPasswordHash);
		userDao.update(user);
	}

	/**
	 * Changes name and persists user.
	 */
	public void updateName(UserModel user, String name) {
		userDao.updateName(user, name);
	}

}