package daos.common;

import models.common.Component;
import models.common.ComponentResult;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.util.List;

/**
 * DAO for ComponentResult entity
 *
 * @author Kristian Lange
 */
@Singleton
public class ComponentResultDao extends AbstractDao {

    @Inject
    ComponentResultDao(JPAApi jpa) {
        super(jpa);
    }

    public void create(ComponentResult componentResult) {
        persist(componentResult);
    }

    public void update(ComponentResult componentResult) {
        merge(componentResult);
    }

    public void remove(ComponentResult componentResult) {
        super.remove(componentResult);
    }

    public void refresh(ComponentResult componentResult) {
        super.refresh(componentResult);
    }

    public ComponentResult findById(Long id) {
        return jpa.em().find(ComponentResult.class, id);
    }

    /**
     * Returns the number of ComponentResults belonging to the given Component.
     */
    public int countByComponent(Component component) {
        String queryStr = "SELECT COUNT(cr) FROM ComponentResult cr WHERE cr.component=:component";
        Query query = jpa.em().createQuery(queryStr);
        Number result = (Number) query.setParameter("component", component).getSingleResult();
        return result.intValue();
    }

    public List<ComponentResult> findAllByComponent(Component component) {
        String queryStr = "SELECT cr FROM ComponentResult cr WHERE cr.component=:component";
        TypedQuery<ComponentResult> query = jpa.em().createQuery(queryStr, ComponentResult.class);
        return query.setParameter("component", component).getResultList();
    }

    public ScrollableResults findAllByComponentScrollable(Component component) {
        String queryStr = "SELECT cr FROM ComponentResult cr WHERE cr.component=:component";
        org.hibernate.query.Query query = (org.hibernate.query.Query) jpa.em().createQuery(queryStr,
                ComponentResult.class);
        return query.setParameter("component", component).scroll(ScrollMode.FORWARD_ONLY);
    }

}
