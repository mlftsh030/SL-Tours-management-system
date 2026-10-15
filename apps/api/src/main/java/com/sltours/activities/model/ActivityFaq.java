package com.sltours.activities.model;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "activity_faqs")
public class ActivityFaq {
    @Id private UUID id = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "activity_id") private Activity activity;
    @Column(nullable = false, length = 300) private String question;
    @Column(nullable = false, length = 2000) private String answer;
    @Column(nullable = false) private int sortOrder;
    protected ActivityFaq() {}
    public ActivityFaq(Activity activity, String question, String answer, int sortOrder) {
        this.activity = activity; this.question = question; this.answer = answer; this.sortOrder = sortOrder;
    }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
}
