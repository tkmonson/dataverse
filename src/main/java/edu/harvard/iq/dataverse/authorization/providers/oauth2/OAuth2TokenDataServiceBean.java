package edu.harvard.iq.dataverse.authorization.providers.oauth2;

import edu.harvard.iq.dataverse.EntityManagerBean;
import java.util.List;
import java.util.Optional;
import javax.ejb.Stateless;
import javax.inject.Inject;

/**
 * CRUD for {@link OAuth2TokenData}.
 * 
 * @author michael
 */
@Stateless
public class OAuth2TokenDataServiceBean {
    
    @Inject
    EntityManagerBean emBean;
    
    public void store( OAuth2TokenData tokenData ) {
        if ( tokenData.getId() != null ) {
            // token exists, this is an update
            emBean.getMaster().merge(tokenData);
            
        } else {
            // ensure there's only one token for each user/service pair.
            emBean.getMaster().createNamedQuery("OAuth2TokenData.deleteByUserIdAndProviderId")
                              .setParameter("userId", tokenData.getUser().getId() )
                              .setParameter("providerId", tokenData.getOauthProviderId() )
                              .executeUpdate();
            emBean.getMaster().persist( tokenData );
        }
    }
    
    public Optional<OAuth2TokenData> get( long authenticatedUserId, String serviceId ) {
        final List<OAuth2TokenData> tokens = emBean.getMaster().createNamedQuery("OAuth2TokenData.findByUserIdAndProviderId", OAuth2TokenData.class)
                .setParameter("userId", authenticatedUserId )
                .setParameter("providerId", serviceId )
                .getResultList();
        return Optional.ofNullable( tokens.isEmpty() ? null : tokens.get(0) );
    }
    
}
