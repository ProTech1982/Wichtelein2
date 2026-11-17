package app.wichtelein2.core

import org.json.JSONArray
import org.json.JSONObject

/** Speichern und Laden des Plans (Tür, Gegenstände, Schritte) als JSON. */
object PlanJson {
    private fun <T : Enum<T>> enumOf(values: Array<T>, name: String?, def: T): T =
        values.firstOrNull { it.name == name } ?: def

    fun toJson(p: Plan): String {
        val o = JSONObject()
        o.put("v", 1)
        o.put("w", p.width)
        o.put("h", p.height)
        o.put("view", p.view.name)
        o.put("horizon", p.horizon.toDouble())
        o.put("ratio", p.wichtelRatio.toDouble())
        o.put("nextId", p.nextId)
        val d = p.door
        o.put(
            "door",
            JSONObject()
                .put("x", d.x.toDouble()).put("y", d.y.toDouble()).put("w", d.w.toDouble()).put("h", d.h.toDouble())
                .put("rot", d.rot.toDouble()).put("shape", d.shape.name).put("hinge", d.hinge.name)
                .put("open", d.openDir.name).put("interior", d.interior.name),
        )
        val objs = JSONArray()
        for (s in p.objects) {
            objs.put(
                JSONObject()
                    .put("id", s.id).put("type", s.type.key)
                    .put("x", s.x.toDouble()).put("y", s.y.toDouble()).put("w", s.w.toDouble()).put("h", s.h.toDouble())
                    .put("rot", s.rot.toDouble()).put("shape", s.shape.name),
            )
        }
        o.put("objects", objs)
        val steps = JSONArray()
        for (s in p.steps) {
            val j = JSONObject()
                .put("action", s.action.key).put("retreat", s.retreat.key)
                .put("object", s.objectId).put("seconds", s.seconds.toDouble())
            if (s.at != null) j.put("ax", s.at.x.toDouble()).put("ay", s.at.y.toDouble())
            steps.put(j)
        }
        o.put("steps", steps)
        return o.toString()
    }

    fun fromJson(text: String): Plan {
        val o = JSONObject(text)
        val w = o.getInt("w")
        val h = o.getInt("h")
        val def = Plan.default(w, h)
        val dj = o.optJSONObject("door")
        val door = if (dj == null) def.door else Door(
            x = dj.optDouble("x", def.door.x.toDouble()).toFloat(),
            y = dj.optDouble("y", def.door.y.toDouble()).toFloat(),
            w = dj.optDouble("w", def.door.w.toDouble()).toFloat(),
            h = dj.optDouble("h", def.door.h.toDouble()).toFloat(),
            rot = dj.optDouble("rot", 0.0).toFloat(),
            shape = enumOf(DoorShape.entries.toTypedArray(), dj.optString("shape"), def.door.shape),
            hinge = enumOf(Hinge.entries.toTypedArray(), dj.optString("hinge"), Hinge.LEFT),
            openDir = enumOf(OpenDir.entries.toTypedArray(), dj.optString("open"), OpenDir.OUT),
            interior = enumOf(InteriorKind.entries.toTypedArray(), dj.optString("interior"), InteriorKind.STUBE),
        )
        val objects = ArrayList<SceneObject>()
        val oa = o.optJSONArray("objects")
        if (oa != null) {
            for (i in 0 until oa.length()) {
                val j = oa.getJSONObject(i)
                val type = ObjType.fromKey(j.optString("type"))
                objects.add(
                    SceneObject(
                        id = j.getInt("id"), type = type,
                        x = j.getDouble("x").toFloat(), y = j.getDouble("y").toFloat(),
                        w = j.getDouble("w").toFloat(), h = j.getDouble("h").toFloat(),
                        rot = j.optDouble("rot", 0.0).toFloat(),
                        shape = enumOf(ShapeKind.entries.toTypedArray(), j.optString("shape"), type.shape),
                    ),
                )
            }
        }
        val steps = ArrayList<Step>()
        val sa = o.optJSONArray("steps")
        if (sa != null) {
            for (i in 0 until sa.length()) {
                val j = sa.getJSONObject(i)
                steps.add(
                    Step(
                        action = ActionType.fromKey(j.optString("action")),
                        at = if (j.has("ax")) Pt(j.getDouble("ax").toFloat(), j.getDouble("ay").toFloat()) else null,
                        retreat = Retreat.fromKey(j.optString("retreat")),
                        objectId = j.optInt("object", -1),
                        seconds = j.optDouble("seconds", 6.0).toFloat(),
                    ),
                )
            }
        }
        return Plan(
            width = w, height = h,
            view = enumOf(ViewMode.entries.toTypedArray(), o.optString("view"), ViewMode.SIDE),
            horizon = o.optDouble("horizon", def.horizon.toDouble()).toFloat(),
            door = door,
            objects = objects,
            steps = if (steps.isEmpty()) def.steps else steps,
            wichtelRatio = o.optDouble("ratio", 0.85).toFloat(),
            nextId = max(o.optInt("nextId", 1), (objects.maxOfOrNull { it.id } ?: 0) + 1),
        )
    }

    private fun max(a: Int, b: Int) = if (a > b) a else b
}
