package edu.harvard.iq.dataverse;

import java.util.List;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.NoResultException;

/**
 *
 * @author michael
 */
@Stateless
@Named
public class MetadataBlockServiceBean {
    
    @Inject
    EntityManagerBean emBean;
    
    public MetadataBlock save(MetadataBlock mdb) {
       return emBean.getMaster().merge(mdb);
    }   
    
    
    public List<MetadataBlock> listMetadataBlocks() {
        return emBean.getMaster().createNamedQuery("MetadataBlock.listAll", MetadataBlock.class).getResultList();
    }
    
    public MetadataBlock findById( Long id ) {
        return emBean.getEntityManager().find(MetadataBlock.class, id);
    }
    
    public MetadataBlock findByName( String name ) {
        try {
            return emBean.getMaster().createNamedQuery("MetadataBlock.findByName", MetadataBlock.class)
                        .setParameter("name", name)
                        .getSingleResult();
        } catch ( NoResultException nre ) {
            return null;
        }
    }
}
