# coding=UTF-8

from java.sql import Timestamp
from java.time import LocalDateTime
from ru.curs.celesta.syscursors import LogCursor

from celestaunit.internal_celesta_unit import CelestaUnit
from simpleCases._simpleCases_orm import getDateForViewCursor, viewWithGetDateCursor, zeroInsertCursor


def preInsert(logCursor):
    logCursor.tablename = "getDateForView"

def postInsert(logCursor):
    logCursor.sessionid = "1"

def preUpdate(logCursor):
    logCursor.tablename = "zeroInsert"

def postUpdate(logCursor):
    logCursor.sessionid = "2"

isPreDeleteDone = False
isPostDeleteDone = False

def preDelete(logCursor):
    global isPreDeleteDone
    isPreDeleteDone = True
    logCursor.tablename = "table2"

def postDelete(logCursor):
    global isPostDeleteDone
    isPostDeleteDone = True
    logCursor.sessionid = "2"


class TestSimpleCases(CelestaUnit):
    def test_getdate_in_view(self):
        tableCursor = getDateForViewCursor(self.context)
        tableCursor.deleteAll()

        viewCursor = viewWithGetDateCursor(self.context)
        self.assertEqual(0, viewCursor.count())

        tableCursor.date = Timestamp.valueOf(LocalDateTime.now().minusDays(1))
        tableCursor.insert()
        self.assertEqual(0, viewCursor.count())

        tableCursor.clear()
        tableCursor.date = Timestamp.valueOf(LocalDateTime.now().plusDays(1))
        tableCursor.insert()
        self.assertEqual(1, viewCursor.count())


    def test_zero_insert(self):
        c = zeroInsertCursor(self.context)
        c.deleteAll()

        c.insert()
        print c.id
        print c.date


    def test_triggers_on_sys_cursors(self):
        c = LogCursor(self.context)

        LogCursor.onPreInsert(preInsert)
        LogCursor.onPostInsert(postInsert)
        LogCursor.onPreUpdate(preUpdate)
        LogCursor.onPostUpdate(postUpdate)
        LogCursor.onPreDelete(preDelete)
        LogCursor.onPostDelete(postDelete)

        c.userid = '1'
        c.sessionid = '0'
        c.grainid = 'simpleCases'
        c.tablename = "zeroInsert"
        c.action_type = "I"
        c.insert()

        self.assertEqual("getDateForView", c.tablename)
        self.assertEqual("1", c.sessionid)

        c.update()

        self.assertEqual("zeroInsert", c.tablename)
        self.assertEqual("2", c.sessionid)

        self.assertFalse(isPreDeleteDone)
        self.assertFalse(isPostDeleteDone)

        c.delete()

        self.assertTrue(isPreDeleteDone)
        self.assertTrue(isPostDeleteDone)