package org.jbei.ice.lib.models;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlTransient;

import org.jbei.ice.lib.dao.IModel;
import org.jbei.ice.lib.models.interfaces.ILinkValueObject;

/**
 * Store url link information.
 * 
 * @author Timothy Ham, Zinovii Dmytriv
 * 
 */
@Entity
@Table(name = "links")
@SequenceGenerator(name = "sequence", sequenceName = "links_id_seq", allocationSize = 1)
public class Link implements ILinkValueObject, IModel {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequence")
    private long id;

    @Column(name = "link", length = 1023)
    private String link;

    @Column(name = "url", length = 1023)
    private String url;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "entries_id", nullable = false)
    private Entry entry;

    public Link() {
    }

    public Link(int id, String link, String url, Entry entry) {
        this.id = id;
        this.link = link;
        this.url = url;
        this.entry = entry;
    }

    @Override
    @XmlTransient
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    @Override
    public String getLink() {
        return link;
    }

    @Override
    public void setLink(String link) {
        this.link = link;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    @XmlTransient
    public Entry getEntry() {
        return entry;
    }

    @Override
    public void setEntry(Entry entry) {
        this.entry = entry;
    }
}
