from typing import List, Literal, Optional

from pydantic import BaseModel as _PydanticBase, Field, field_validator, model_validator



class BaseModel(_PydanticBase):
    """Treat explicit nulls from the Java side as 'use the default'."""

    @model_validator(mode="before")
    @classmethod
    def _drop_nulls(cls, v):
        if isinstance(v, dict):
            return {k: x for k, x in v.items() if x is not None}
        return v


Level = Literal["BEGINNER", "INTERMEDIATE", "ADVANCED"]


class SkillIn(BaseModel):
    skill_id: Optional[int] = None
    name: str = Field(min_length=1)
    proficiency: Level = "BEGINNER"

    @field_validator("proficiency", mode="before")
    @classmethod
    def up(cls, v):
        return (v or "BEGINNER").upper()


class RequiredSkill(BaseModel):
    skill_id: Optional[int] = None
    name: str = Field(min_length=1)
    importance: int = Field(3, ge=1, le=5)
    required_level: Level = "INTERMEDIATE"


class CareerGoal(BaseModel):
    id: Optional[int] = None
    name: str
    required_skills: List[RequiredSkill] = []


class Student(BaseModel):
    id: Optional[int] = None
    user_id: Optional[int] = None
    name: str = "Student"
    career_goal: Optional[CareerGoal] = None
    interests: List[str] = []
    availability: List[str] = []
    skills: List[SkillIn] = []


class AcademicRecord(BaseModel):
    subject: str
    percentage: float = Field(ge=0, le=100)
    semester: Optional[int] = None
    academic_year: Optional[str] = None


class Attendance(BaseModel):
    subject: str
    percentage: float = Field(ge=0, le=100)
    semester: Optional[int] = None


class Assignment(BaseModel):
    subject: str
    status: str = "SUBMITTED"
    score_pct: Optional[float] = Field(None, ge=0, le=100)


class Academics(BaseModel):
    records: List[AcademicRecord] = []
    attendance: List[Attendance] = []
    assignments: List[Assignment] = []


class CatalogSkill(BaseModel):
    id: Optional[int] = None
    name: str
    category: str = ""
    description: str = ""
    difficulty: str = "BEGINNER"
    estimated_hours: float = 20
    practice_task: Optional[str] = None
    prerequisite_ids: List[int] = []
    prerequisites: List[str] = []


class Candidate(BaseModel):
    user_id: int
    name: str
    type: Literal["PEER", "MENTOR"] = "PEER"
    career_goal: Optional[str] = None
    interests: List[str] = []
    availability: List[str] = []
    skills: List[SkillIn] = []
    goal_skills: List[str] = []
    trust_score: Optional[float] = None


class Resource(BaseModel):
    id: Optional[int] = None
    skill_id: Optional[int] = None
    skill: Optional[str] = None
    title: str
    type: str = "Article"
    url: Optional[str] = None
    difficulty: str = "BEGINNER"
    estimated_minutes: Optional[int] = None


class Feedback(BaseModel):
    recent_progress_notes: List[str] = []
    low_rated_user_ids: List[int] = []


class RoadmapState(BaseModel):
    in_progress_skill_ids: List[int] = []
    completed_skill_ids: List[int] = []


class AnalysisRequest(BaseModel):
    student: Student
    academics: Academics = Academics()
    skill_catalog: List[CatalogSkill] = []
    candidates: List[Candidate] = []
    resources: List[Resource] = []
    feedback: Feedback = Feedback()
    roadmap_state: RoadmapState = RoadmapState()


class RetrainRequest(BaseModel):
    labelled_rows: List[dict] = []
