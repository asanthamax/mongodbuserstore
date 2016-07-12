package org.wso2.carbon.mongodb.util;

import com.mongodb.DB;
import com.mongodb.DBCursor;
import org.apache.axiom.om.util.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.mongodb.query.MongoPreparedStatement;
import org.wso2.carbon.mongodb.query.MongoPreparedStatementImpl;
import org.wso2.carbon.mongodb.query.MongoQueryException;
import org.wso2.carbon.mongodb.userstoremanager.MongoDBRealmConstants;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.authorization.DBConstants;
import org.wso2.carbon.user.core.common.UserStore;
import org.wso2.carbon.user.core.dto.RoleDTO;
import org.wso2.carbon.user.core.jdbc.JDBCRealmConstants;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;
import org.wso2.carbon.utils.xml.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to handle user kernel utilities.
 */
public final class MongoUserCoreUtil {

    private static final String DUMMY_VALUE = "dummy";
    private static final String APPLICATION_DOMAIN = "Application";
    private static final String WORKFLOW_DOMAIN = "Workflow";
    private static Log log = LogFactory.getLog(MongoUserCoreUtil.class);
    private static Boolean isEmailUserName;
    private static Boolean isCrossTenantUniqueUserName;
    private static RealmService realmService = null;
    /*
     * When user authenticates with out domain, need to set the domain of the user store that he
     * belongs to, as a thread local variable.
     */
    private static ThreadLocal<String> threadLocalToSetDomain = new ThreadLocal<String>();

    /**
     * @param arr1
     * @param arr2
     * @return
     * @throws UserStoreException
     */
    public static String[] combineArrays(String[] arr1, String[] arr2) throws UserStoreException {
        if (arr1 == null || arr1.length == 0) {
            return arr2;
        }
        if (arr2 == null || arr2.length == 0) {
            return arr1;
        }
        String[] newArray = new String[arr1.length + arr2.length];
        for (int i = 0; i < arr1.length; i++) {
            newArray[i] = arr1[i];
        }

        int j = 0;
        for (int i = arr1.length; i < newArray.length; i++) {
            Arrays.toString(newArray);
            newArray[i] = arr2[j];
            j++;
        }
        return newArray;
    }

    /**
     * @param array
     * @param list
     * @return
     * @throws UserStoreException
     */
    public static String[] combine(String[] array, List<String> list) throws UserStoreException {

        if(array == null || list == null){
            throw new IllegalArgumentException("Invalid parameters; array : " + array + ", list : " + list);
        }
        Set h = new HashSet(list);
        h.addAll(Arrays.asList(array));
        return (String[]) h.toArray(new String[h.size()]);
    }

    /**
     * @param rawResourcePath
     * @return
     */
    public static String[] optimizePermissions(String[] rawResourcePath) {
        Arrays.sort(rawResourcePath);
        int index = 0;
        List<String> lst = new ArrayList<String>();
        while (index < rawResourcePath.length) {
            String shortestString = rawResourcePath[index];
            lst.add(shortestString);
            index++;
            Pattern p = Pattern.compile("(.*)/.*$");
            while (index < rawResourcePath.length) {
                Matcher m = p.matcher(rawResourcePath[index]);
                if (m.find()) {
                    String s = m.group(1);
                    if (s.equals(shortestString)) {
                        index++;
                    } else {
                        break;
                    }
                }
            }
        }
        return lst.toArray(new String[lst.size()]);
    }

    /**
     * @return
     */
    public static Boolean getIsEmailUserName() {
        return isEmailUserName;
    }

    /**
     * @return
     */
    public static RealmService getRealmService() {
        return realmService;
    }

    /**
     * @param realmService
     */
    public static void setRealmService(RealmService realmService) {
        MongoUserCoreUtil.realmService = realmService;
    }

    /**
     * @return
     */
    public static Boolean getIsCrossTenantUniqueUserName() {
        return isCrossTenantUniqueUserName;
    }

    /**
     * @param password
     * @param passwordHashMethod
     * @param isKdcEnabled
     * @return
     * @throws UserStoreException
     */
    public static String getPasswordToStore(String password, String passwordHashMethod,
                                            boolean isKdcEnabled) throws UserStoreException {

        if (isKdcEnabled) {
            // If KDC is enabled we will always use plain text passwords.
            // Cause - KDC cannot operate with hashed passwords.

            return password;
        }

        String passwordToStore = password;

        if (passwordHashMethod != null) {

            if (passwordHashMethod
                    .equals(UserCoreConstants.RealmConfig.PASSWORD_HASH_METHOD_PLAIN_TEXT)) {
                return passwordToStore;
            }

            try {
                MessageDigest messageDigest = MessageDigest.getInstance(passwordHashMethod);
                byte[] digestValue = messageDigest.digest(password.getBytes());
                passwordToStore = "{" + passwordHashMethod + "}" + Base64.encode(digestValue);
//				passwordToStore = Base64.encode(digestValue);
            } catch (NoSuchAlgorithmException e) {
                throw new UserStoreException("Invalid hashMethod", e);
            }
        }
        return passwordToStore;
    }

    /**
     * @param realmConfig
     * @return
     */
    public static boolean isKdcEnabled(RealmConfiguration realmConfig) {

        String stringKdcEnabled = realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_KDC_ENABLED);

        if (stringKdcEnabled != null) {
            return Boolean.parseBoolean(stringKdcEnabled);
        } else {
            return false;
        }
    }

    /**
     * @return
     */
    public static String getDummyPassword() {
        SecureRandom rand = new SecureRandom();
        return DUMMY_VALUE + rand.nextInt(999999);
    }

    /**
     * check whether value is contain in String array case insensitivity way
     *
     * @param name
     * @param names
     * @return
     */
    public static boolean isContain(String name, String[] names) {

        if (name == null || names == null || names.length == 0) {
            return false;
        }

        for (String n : names) {
            if (name.equalsIgnoreCase(n)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method generates a random password that adhere to most of the password policies defined
     * by various LDAPs such as AD, ApacheDS 2.0 etc
     *
     * @param username
     * @return
     * @throws UserStoreException
     */
    public static String getPolicyFriendlyRandomPassword(String username) throws UserStoreException {
        return getPolicyFriendlyRandomPassword(username, 8);
    }

    /**
     * This method generates a random password that adhere to most of the password policies defined
     * by various LDAPs such as AD, ApacheDS 2.0 etc
     *
     * @param username
     * @param length
     * @return password
     * @throws UserStoreException
     */
    public static String getPolicyFriendlyRandomPassword(String username, int length)
            throws UserStoreException {

        if (length < 8 || length > 50) {
            length = 12;
        }

        // Avoiding admin, administrator, root, wso2, carbon to be a password
        char[] chars = {'E', 'F', 'G', 'H', 'J', 'K', 'L', 'N', 'P', 'Q', 'U', 'V', 'W', 'X', 'Y',
                'Z', 'e', 'f', 'g', 'h', 'j', 'k', 'l', 'n', 'p', 'q', 'u', 'v', 'w', 'x', 'y',
                'z', '~', '!', '@', '#', '$', '%', '^', '&', '*', '_', '-', '+', '=',};

        char[] invalidChars = username.toCharArray();
        StringBuffer passwordFeed = new StringBuffer();

        // now we are going filter characters in the username
        for (char invalidCha : invalidChars) {
            for (char cha : chars) {
                if (cha != invalidCha)
                    passwordFeed.append(cha);
            }
        }

        // the password generation
        String passwordChars = passwordFeed.toString();
        char[] password = new char[length];
        String randomNum = null;

        try {
            // the secure random
            SecureRandom prng = SecureRandom.getInstance("SHA1PRNG");
            for (int i = 0; i < length; i++) {
                password[i] = passwordChars.charAt(prng.nextInt(passwordFeed.length()));
            }
            randomNum = Integer.toString(prng.nextInt());

        } catch (NoSuchAlgorithmException e) {
            String errorMessage = "Error while creating the random password for user : " + username;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        }

        return new String(password).concat(randomNum);
    }

    /**
     * @param roleNames
     * @param domain
     * @return
     */
    public static RoleDTO[] convertRoleNamesToRoleDTO(String[] roleNames, String domain) {
        if (roleNames != null && roleNames.length != 0) {
            List<RoleDTO> dtos = new ArrayList<RoleDTO>();
            for (String roleName : roleNames) {
                RoleDTO dto = new RoleDTO();
                dto.setRoleName(roleName);
                dto.setDomainName(domain);
                dtos.add(dto);
            }
            return dtos.toArray(new RoleDTO[dtos.size()]);
        }
        return null;
    }

    /**
     * @param domain
     */
    public static void setDomainInThreadLocal(String domain) {
        if (domain != null && !UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME.equalsIgnoreCase(domain)) {
            threadLocalToSetDomain.set(domain.toUpperCase());
        }

        if (domain == null || (UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME.equalsIgnoreCase
                (domain) && threadLocalToSetDomain.get() != null)) {
            // clear the thread local variable.
            threadLocalToSetDomain.remove();
        }
    }

    /**
     * @return
     */
    public static String getDomainFromThreadLocal() {
        return (String) threadLocalToSetDomain.get();
    }

    /**
     * @param name
     * @return
     */
    public static String removeDomainFromName(String name) {

        int index;
        if ((index = name.indexOf(CarbonConstants.DOMAIN_SEPARATOR)) >= 0) {
            // remove domain name if exist
            name = name.substring(index + 1);
        }
        return name;
    }

    /**
     * Removes the entry name if the name is in the base.
     *
     * @param base
     * @param entryName
     * @param nameAttribute
     * @return
     */
    public static String formatSearchBase(String base, String entryName, String nameAttribute) {
        entryName = removeDomainFromName(entryName);
        String key = nameAttribute + "=" + entryName;
        if (base.indexOf(key) >= 0) {
            String[] arr = base.split(key);
            base = "";
            StringBuffer buf = new StringBuffer();
            for (String s : arr) {
                buf.append(s);
            }
            base = buf.toString();
            if (base.startsWith(",")) {
                base = base.substring(1);
            }
            if (base.endsWith(",")) {
                base = base.substring(0, base.length() - 1);
            }
            return base;
        }
        return base;
    }

    /**
     * @param name
     * @return
     */
    public static String removeDistinguishedName(String name) {

        int index;
        if ((index = name.indexOf(UserCoreConstants.TENANT_DOMAIN_COMBINER)) >= 0) {
            name = name.substring(0, index);
        }
        return name;
    }

    /**
     * @param name
     * @return
     */
    public static String addInternalDomainName(String name) {

        if (name.indexOf(CarbonConstants.DOMAIN_SEPARATOR) < 0) {
            // domain name is not already appended, and if exist in user-mgt.xml, append it..
            // append domain name if exist
            name = UserCoreConstants.INTERNAL_DOMAIN + CarbonConstants.DOMAIN_SEPARATOR + name;
        }
        return name;
    }

    /**
     * @param name
     * @return
     */
    public static String setDomainToUpperCase(String name) {

        int index = name.indexOf(CarbonConstants.DOMAIN_SEPARATOR);

        if (index > 0) {
            String domain = name.substring(0, index);
            name = domain.toUpperCase() + name.substring(index);
        }

        return name;
    }

    /**
     * @param domainName
     * @return
     */
    public static String addDomainToName(String name, String domainName) {

        if ((name.indexOf(UserCoreConstants.DOMAIN_SEPARATOR)) < 0 &&
                !UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME.equalsIgnoreCase(domainName)) {
            // domain name is not already appended, and if exist in user-mgt.xml, append it..
            if (domainName != null) {
                // append domain name if exist
                domainName = domainName.toUpperCase() + CarbonConstants.DOMAIN_SEPARATOR;
                name = domainName + name;
            }
        }
        return name;
    }

    /**
     * Append the distinguished name to the tenantAwareEntry name
     *
     * @param tenantAwareEntry
     * @param tenantDomain
     * @return
     */
    public static String addTenantDomainToEntry(String tenantAwareEntry, String tenantDomain) {

        if (StringUtils.isEmpty(tenantAwareEntry)){
            throw new IllegalArgumentException();
        } else if (!StringUtils.isEmpty(tenantDomain)) {
            return tenantAwareEntry + UserCoreConstants.TENANT_DOMAIN_COMBINER + tenantDomain;
        } else {
            return tenantAwareEntry + UserCoreConstants.TENANT_DOMAIN_COMBINER + MultitenantConstants
                    .SUPER_TENANT_DOMAIN_NAME;
        }
    }

    /**
     * @param realmConfig
     * @return
     */
    public static String getDomainName(RealmConfiguration realmConfig) {
        return realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
    }

    /**
     * Domain name is not already appended, and if it is provided or if exist in user-mgt.xml,
     * append it
     *
     * @param names
     * @param domainName
     * @return
     */
    public static String[] addDomainToNames(String[] names, String domainName) {

        if (domainName != null) {
            domainName = domainName.toUpperCase();
        }

        List<String> namesList = new ArrayList<String>();
        if (names != null && names.length != 0) {
            for (String name : names) {
                if ((name.indexOf(UserCoreConstants.DOMAIN_SEPARATOR)) < 0 &&
                        !UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME.equalsIgnoreCase(domainName)) {
                    if (domainName != null) {
                        name = MongoUserCoreUtil.addDomainToName(name, domainName);
                        namesList.add(name);
                        continue;
                    }
                }
                namesList.add(name);
            }
        }
        if (namesList.size() != 0) {
            return namesList.toArray(new String[namesList.size()]);
        } else {
            return names;
        }
    }

    /**
     * @param names
     * @return
     */
    public static String[] removeDomainFromNames(String[] names) {
        List<String> nameList = new ArrayList<String>();
        int index;
        if (names != null && names.length != 0) {
            for (String name : names) {
                if ((index = name.indexOf(UserCoreConstants.DOMAIN_SEPARATOR)) > 0) {
                    String domain = name.substring(0, index);
                    if (!UserCoreConstants.INTERNAL_DOMAIN.equalsIgnoreCase(domain)
                            && !APPLICATION_DOMAIN.equalsIgnoreCase(domain) && !WORKFLOW_DOMAIN.equalsIgnoreCase(domain)) {
                        // remove domain name if exist
                        nameList.add(name.substring(index + 1));
                    } else {
                        nameList.add(name);
                    }
                }
            }
        }
        if (nameList.size() != 0) {
            return nameList.toArray(new String[nameList.size()]);
        } else {
            return names;
        }
    }

    /**
     * @param domainName
     * @param userName
     * @param displayName
     * @return
     */
    public static String getCombinedName(String domainName, String userName, String displayName) {
        /*
		 * get the name in combined format if two different values are there for userName &
		 * displayName format: domainName/userName|domainName/displayName
		 */
        // if name and display name are equal, keep only one
        String combinedName = null;
        if (domainName != null &&
                !UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME.equalsIgnoreCase(domainName)) {
            domainName = domainName.toUpperCase() + UserCoreConstants.DOMAIN_SEPARATOR;
            if ((!userName.equals(displayName)) && (displayName != null)) {
                userName = domainName + userName;
                displayName = domainName + displayName;
                combinedName = userName + UserCoreConstants.NAME_COMBINER + displayName;
            } else {
                combinedName = domainName + userName;
            }
        } else {
            if (!userName.equals(displayName) && displayName != null) {
                combinedName = userName + UserCoreConstants.NAME_COMBINER + displayName;
            } else {
                combinedName = userName;
            }
        }
        return combinedName;
    }

    /**
     * @param userName
     * @param realmConfig
     * @return
     */
    public static boolean isPrimaryAdminUser(String userName, RealmConfiguration realmConfig) {

        String myDomain = getDomainName(realmConfig);

        if (myDomain != null) {
            myDomain += CarbonConstants.DOMAIN_SEPARATOR;
        }

        if (realmConfig.isPrimary()) {
            if (realmConfig.getAdminUserName().equalsIgnoreCase(userName)
                    || realmConfig.getAdminUserName().equalsIgnoreCase(myDomain + userName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @param roleName
     * @param realmConfig
     * @return
     */
    public static boolean isPrimaryAdminRole(String roleName, RealmConfiguration realmConfig) {

        String myDomain = getDomainName(realmConfig);

        if (myDomain != null) {
            myDomain += CarbonConstants.DOMAIN_SEPARATOR;
        }

        if (realmConfig.isPrimary()) {
            if (realmConfig.getAdminRoleName().equalsIgnoreCase(roleName)
                    || realmConfig.getAdminRoleName().equalsIgnoreCase(myDomain + roleName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @param roleName
     * @param realmConfig
     * @return
     */
    public static boolean isEveryoneRole(String roleName, RealmConfiguration realmConfig) {

        String myDomain = UserCoreConstants.INTERNAL_DOMAIN;

        if (myDomain != null) {
            myDomain += CarbonConstants.DOMAIN_SEPARATOR;
        }

        if (realmConfig.isPrimary() && realmConfig.getEveryOneRoleName() != null
                && (realmConfig.getEveryOneRoleName().equalsIgnoreCase(roleName))
                || realmConfig.getEveryOneRoleName().equalsIgnoreCase(myDomain + roleName)) {
            return true;
        }
        return false;
    }

    /**
     * @param
     * @param realmConfig
     * @return
     */
    public static boolean canRoleBeRenamed(UserStore oldStore, UserStore newStore,
                                           RealmConfiguration realmConfig) {

        if (oldStore.getDomainName() == null && newStore.getDomainName() != null) {
            return false;
        }

        if (oldStore.getDomainName() != null
                && !oldStore.getDomainName().equalsIgnoreCase(newStore.getDomainName())) {
            return false;
        }

        if ((oldStore.isHybridRole() && realmConfig
                .isReservedRoleName(oldStore.getDomainFreeName()))
                || (newStore.isHybridRole() && realmConfig.isReservedRoleName(newStore
                .getDomainFreeName()))) {
            return false;
        }

        return true;
    }

    /**
     * @param userName
     * @param
     * @return
     */
    public static boolean isRegistryAnnonymousUser(String userName) {

        if (CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME.equalsIgnoreCase(userName)) {
            return true;
        }

        return false;
    }

    /**
     * @param userName
     * @param
     * @return
     */
    public static boolean isRegistrySystemUser(String userName) {

        if (CarbonConstants.REGISTRY_SYSTEM_USERNAME.equalsIgnoreCase(userName)) {
            return true;
        }

        return false;
    }

    public static String extractDomainFromName(String nameWithDomain) {
        int index;
        if ((index = nameWithDomain.indexOf(CarbonConstants.DOMAIN_SEPARATOR)) > 0) {
            // extract the domain name if exist
            String names[] = nameWithDomain.split(CarbonConstants.DOMAIN_SEPARATOR);
            return names[0];
        }
        return UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME;
    }

    public static void persistDomain(String domain, int tenantId, DB dataSource) throws UserStoreException {
        DB dbConnection = null;
        try {
            String mongoStatement = MongoDBRealmConstants.ADD_DOMAIN_MONGO_QUERY;

            if (domain != null) {
                domain = domain.toUpperCase();
            }

            if (!isExistingDomain(domain, tenantId, dataSource)) {
                dbConnection = dataSource;
                Map<String,Object> map = new HashMap<String, Object>();
                map.put("UM_TENANT_ID",tenantId);
                map.put("UM_DOMAIN_NAME",domain);
                MongoDatabaseUtil.updateDatabase(dbConnection, mongoStatement, map);
            }
        } catch (UserStoreException e) {
            String errorMessage =
                    "Error occurred while checking is existing domain : " + domain + " for tenant : " + tenantId;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } catch (Exception e) {
            String errorMessage =
                    "DB error occurred while persisting domain : " + domain + " & tenant id : " + tenantId;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            MongoDatabaseUtil.closeConnection(dbConnection);
        }

    }

    public static void deletePersistedDomain(String domain, int tenantId, DB dataSource)
            throws UserStoreException {
        DB dbConnection = null;
        try {
            String mongoStatement = MongoDBRealmConstants.DELETE_DOMAIN_MONGO_QUERY;
            Map<String,Object> map = new HashMap<String, Object>();
            map.put("UM_DOMAIN_NAME",domain);
            map.put("UM_TENANT_ID",tenantId);
            if (domain != null) {
                domain = domain.toUpperCase();
            }

            if (isExistingDomain(domain, tenantId, dataSource)) {
                dbConnection = dataSource;
                MongoDatabaseUtil.deleteFromDatabase(dbConnection, mongoStatement, map);
            }
        } catch (UserStoreException e) {
            String errorMessage =
                    "Error occurred while deleting domain : " + domain + " for tenant : " + tenantId;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } catch (Exception e) {
            String errorMessage =
                    "DB error occurred while deleting domain : " + domain + " & tenant id : " + tenantId;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {

            MongoDatabaseUtil.closeConnection(dbConnection);
        }
    }

    public static void updatePersistedDomain(String previousDomain, String newDomain, int tenantId,
                                             DB dataSource) throws UserStoreException {
        DB dbConnection = null;
        try {
            String mongoStatement = MongoDBRealmConstants.UPDATE_DOMAIN_MONGO_QUERY;
            Map<String,Object> map = new HashMap<String, Object>();
            if (previousDomain != null) {
                previousDomain = previousDomain.toUpperCase();
            }
            if (newDomain != null) {
                newDomain = newDomain.toUpperCase();
            }

            // check for previous domain exists
            if (isExistingDomain(previousDomain, tenantId, dataSource)) {

                // New domain already exists, delete it first
                if (!isExistingDomain(newDomain, tenantId, dataSource)) {
                    deletePersistedDomain(newDomain, tenantId, dataSource);
                }

                // Now rename the domain name
                dbConnection = dataSource;
                map.put("UM_DOMAIN_NAME",newDomain);
                map.put("UM_TENANT_ID",tenantId);
                MongoDatabaseUtil.updateDatabase(dbConnection, mongoStatement, map);
            }
        } catch (UserStoreException e) {
            String errorMessage =
                    "Error occurred while updating domain : " + previousDomain + " to new domain : " + newDomain +
                            " for tenant : " + tenantId;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } catch (Exception e) {
            String errorMessage =
                    "DB error occurred while updating domain : " + previousDomain + " to new domain : " + newDomain +
                            " for tenant : " + tenantId;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            MongoDatabaseUtil.closeConnection(dbConnection);
        }
    }

    private static boolean isExistingDomain(String domain, int tenantId, DB dataSource) throws UserStoreException{

        DB dbConnection = null;
        MongoPreparedStatement prepStmt = null;
        DBCursor cursor = null;
        boolean isExisting = false;

        try {
            dbConnection = dataSource;
            prepStmt = new MongoPreparedStatementImpl(dbConnection,MongoDBRealmConstants.IS_DOMAIN_EXISTING_MONGO_QUERY);
            if (domain != null) {
                domain = domain.toUpperCase();
            }
            prepStmt.setString("UM_DOMAIN_NAME", domain);
            prepStmt.setInt("UM_TENANT_ID", tenantId);
            cursor = prepStmt.find();
            if (cursor.hasNext()) {
                isExisting = true;
            }
            return isExisting;
        } catch (Exception e) {
            String errorMessage =
                    "DB error occurred while checking is existing domain : " + domain + " & tenant id : " + tenantId;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            MongoDatabaseUtil.closeConnection(dbConnection);
        }
    }

    private static boolean checkExistingDomainId(int domainId, int tenantId, DB dataSource)
            throws UserStoreException {
        DB dbConnection = null;
        MongoPreparedStatement prepStmt = null;
        DBCursor cursor = null;
        boolean isExisting = false;

        try {
            dbConnection = dataSource;
            prepStmt = new MongoPreparedStatementImpl(dbConnection,MongoDBRealmConstants.CHECK_DOMAIN_ID_EXISTING_MONGO_QUERY);
            prepStmt.setInt("UM_DOMAIN_ID", domainId);
            prepStmt.setInt("UM_TENANT_ID", tenantId);
            cursor = prepStmt.find();
            if (cursor.hasNext()) {
                int value = Integer.parseInt(cursor.next().get("UM_DOMAIN_ID").toString());
                if (domainId == value) {
                    isExisting = true;
                }
            }
            return isExisting;
        } catch (Exception e) {
            String errorMessage =
                    "DB error occurred while checking is existing domain id : " + domainId + " & tenant id : " +
                            tenantId;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            MongoDatabaseUtil.closeConnection(dbConnection);
        }
    }

    public static boolean isSystemRole(String roleName, int tenantId, DB dataSource)
            throws UserStoreException {
        DB dbConnection = null;
        MongoPreparedStatement prepStmt = null;
        DBCursor cursor = null;
        boolean isExisting = false;

        try {
            dbConnection = dataSource;
            prepStmt = new MongoPreparedStatementImpl(dbConnection,DBConstants.IS_SYSTEM_ROLE);
            prepStmt.setString("UM_ROLE_NAME", roleName);
            prepStmt.setInt("UM_TENANT_ID", tenantId);
            cursor = prepStmt.find();
            if (cursor.hasNext()) {
                int value = Integer.parseInt(cursor.next().get("UM_ID").toString());
                if (value > -1) {
                    isExisting = true;
                }
            }
            return isExisting;
        } catch (Exception e) {
            String errorMessage =
                    "DB error occurred while checking is existing system role for : " + roleName + " & tenant id : " +
                            tenantId;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            MongoDatabaseUtil.closeConnection(dbConnection);
        }
    }

    /**
     * Returns the shared group RDN for the tenants
     *
     * @param tenantOu
     * @return
     */
    public static String getTenantShareGroupBase(String tenantOu) {
        return tenantOu + "=" + CarbonContext.getThreadLocalCarbonContext().getTenantDomain();
    }
}
