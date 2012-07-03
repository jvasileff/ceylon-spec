/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.redhat.ceylon.compiler.typechecker.util;

import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

/**
 *
 * @author kulikov
 */
public class ReferenceCounter extends Visitor {
    @Override
    public void visit(Tree.MemberOrTypeExpression that) {
        super.visit(that);
        that.getDeclaration().incRefCount();
    }
    @Override
    public void visit(Tree.Type that) {
        super.visit(that);
        that.getTypeModel().getDeclaration().incRefCount();
    }
}