#!/usr/bin/python

"""
usage: mkresources.py input_file
"""

import os
import sys
import re
import xml.etree.ElementTree as xml
# import xml.dom.minidom as minidom
import tempfile
import atexit
# pycparser -- "pip install pycparser" or "easy_install pycparser"
import pycparser
# GCC extensions to pycparser, available on GitHub:
#	git clone https://github.com/inducer/pycparserext.git
#	cd pycparserext
#	python setup.py install
from pycparserext.ext_c_parser import GnuCParser

#------------------------------------------------------------------------

def main(input_file, temp_file):
	# Make the AST out of the C source code.  "cpp" on OSX doesn't
	# behave correctly, use "gcc -E" instead (which is necessary
	# regardless, as there are plenty of flags required to get
	# the preprocessor to emit anything cleanly anyways) The
	# search paths should be UQM, UQM-EE, and P6014 compatible
	#
	# Notes:
	# There's something in one of the headers that confuses the
	# parser on OSX; it's dependent on the existence of the
	# __BLOCKS__ macro, which seems unrelated to anything I
	# need, so undef it to make things run smoothly.
	# 
	# The -include switch, and the temp_file, are explained at
	# the end of this file.
	#
	cpp_args = [
				"-E",
				"-U __BLOCKS__",
				"-Ibuild/macosx",
				"-I.",
				"-Isrc",
				"-Isrc/sc2code",
				"-Isrc/sc2code/libs",
				"-Isrc/uqm",
				"-Isrc/libs",
				"-include%s" % os.path.basename(temp_file),
	]

	t = pycparser.parse_file(
			input_file,
			use_cpp=True,
			cpp_path="gcc",
			cpp_args=cpp_args,
			parser=GnuCParser()
	)

	# Make a mapping of the LOCDATA struct names and the offsets
	ld = LOCDATA_Visitor()
	ld.visit(t)
	wtf = WTF()
	wtf.visit(ld.value)
	del(ld)

	# Find all of the "*_desc*" structs in the input file.
	# These would be LOCDATA structs in the top-most file scope.
	# UQM/UQM-EE have 1 per file; P6014 has 3 per file
	descs = []
	for child_name, child in t.children():
		if child.__class__.__name__ == "Decl":
			try:
				if "LOCDATA" in child.type.type.names:
					if child.coord.file == input_file:
						descs.append(child)
			except:
				pass

	# print wtf.struct
	for desc in descs:
		_, fields = desc.init.children()[wtf.struct["AlienAmbientArray"]]
		emit = Emit(fields)

		race = re.sub("(_desc|_1x)", "", desc.name)
		print "<!-- filename: %s.xml -->" % race
		print "<!-- These values taken from The Ur-Quan Masters sources -->"

		output = xml.Element("resources")
		ias = []
		for index, value in enumerate(emit.retval):
			name = "%s_anim%d" % (race, index)
			ia = xml.SubElement(output, "integer-array", name=name)
			ias.append(name)
			for val in value:
				x = xml.SubElement(ia, "item")
				x.text = str(val)

		sa = xml.SubElement(output, "string-array", name=race)
		for ia in ias:
			x = xml.SubElement(sa, "item")
			x.text = ia

		# Run through "xmllint --encode utf-8 --format -"
		# for pretty-printing (xml.etree.ElementTree has
		# none, and xml.dom.minidom's is awful-looking)
		#
		xml.dump(output)

#------------------------------------------------------------------------

class LOCDATA_Visitor(pycparser.c_ast.NodeVisitor):
	value = None
	def visit_Typedef(self, node):
		if self.value is None:
			if node.name == "LOCDATA":
				self.value = node

class WTF(pycparser.c_ast.NodeVisitor):
	"""
	I can't figure out how to deal with child nodes in a single class.
	So make multiple NodeVisitor subclasses based on the node type.
	"""
	struct = {}
	index = 0
	def visit_Decl(self, node):
		self.struct[node.name] = self.index
		self.index += 1

#------------------------------------------------------------------------

class Emit(object):
	"""	Emit Python data structures from the AST input.
		Stolen from the CGenerator class"""

	def __init__(self, input):
		self.retval = eval("(" + self.visit(input) + ")")

	def visit(self, node):
		method = "visit_" + node.__class__.__name__
		return getattr(self, method, self.generic_visit)(node)

	def generic_visit(self, node):
		if node is None:
			return ""
		else:
			return " ".join(self.visit(c) for c in node.children())

	def visit_ExprList(self, n):
		visited_subexprs = []
		for expr in n.exprs:
			if isinstance(expr, pycparser.c_ast.ExprList):
				visited_subexprs.append("(" + self.visit(expr) + ")")
			else:
				visited_subexprs.append(self.visit(expr))
		return ",".join(visited_subexprs)

	def visit_BinaryOp(self, n):
		lval = self._parenthesize_unless_simple(n.left)
		rval = self._parenthesize_unless_simple(n.right)
		return "%s %s %s" % (lval, n.op, rval)

	def visit_Constant(self, n):
		return n.value

	def _parenthesize_if(self, n, condition):
		""" Visits "n" and returns its string representation, parenthesized
			if the condition function applied to the node returns True.
		"""
		s = self.visit(n)
		if condition(n):
			return "(" + s + ")"
		else:
			return s

	def _parenthesize_unless_simple(self, n):
		""" Common use case for _parenthesize_if
		"""
		return self._parenthesize_if(n, lambda d: not self._is_simple_node(d))

	def _is_simple_node(self, n):
		""" Returns True for nodes that are "simple" - i.e. nodes that always
			have higher precedence than operators.
		"""
		return isinstance(n,(   pycparser.c_ast.Constant,
								pycparser.c_ast.ID,
								pycparser.c_ast.ArrayRef,
								pycparser.c_ast.StructRef,
								pycparser.c_ast.FuncCall))
#------------------------------------------------------------------------

if __name__ == "__main__":
	tmpf, tmp = tempfile.mkstemp(suffix=".h", dir=".")
	# Need to redefine the ONE_SECOND constant, but GCC 4.2.x
	# doesn't like multiple macro definitions.  The proper way
	# to fix this is to armor the declaration against this and
	# use -D, but that does nothing for the sources out in the
	# wild.  Yuck.
	#
	os.write(tmpf, """
		#ifndef _TIMLIB_H
		#define _TIMLIB_H
		#define TIMELIB SDL
		#include "libs/compiler.h"
		#if TIMELIB == SDL
		#	define ONE_SECOND 1000
		#endif
		typedef DWORD TimeCount;
		typedef DWORD TimePeriod;
		extern void InitTimeSystem (void);
		extern void UnInitTimeSystem (void);
		extern TimeCount GetTimeCounter (void);
		#endif
	""".replace("	", ""))
	os.close(tmpf)

	@atexit.register
	def cleanup():
		os.unlink(tmp)

	for arg in sys.argv[1:]:
		main(arg, tmp)

#------------------------------------------------------------------------
